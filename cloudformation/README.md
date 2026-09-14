# CloudFormation deployment

`velociraptor-app.yaml` deploys the server to ECS Fargate behind an existing Application Load
Balancer, with blue/green releases driven by CodeDeploy.

This is a reference deployment, not a turnkey one: it assumes you already run a VPC, an ALB and an
ECR repository, and every account-specific value is a parameter. The examples below use the
placeholder account id `123456789012` and region `eu-west-2` — substitute your own.

## Prerequisites

- A VPC with at least two private subnets in different availability zones
- An ALB with an HTTPS listener
- An ECR repository holding a velociRAPTOR image (see `build-and-push.sh`)
- An S3 bucket holding the GTFS feed to serve

## Deploy

```bash
aws cloudformation create-stack \
  --stack-name velociraptor-prod \
  --template-body file://velociraptor-app.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameters \
    ParameterKey=Environment,ParameterValue=prod \
    ParameterKey=VpcId,ParameterValue=vpc-xxxxx \
    ParameterKey=PrivateSubnetIds,ParameterValue="subnet-xxxxx,subnet-yyyyy" \
    ParameterKey=AlbListenerArn,ParameterValue=arn:aws:elasticloadbalancing:eu-west-2:123456789012:listener/app/my-alb/xxxxx/yyyyy \
    ParameterKey=ContainerImage,ParameterValue=123456789012.dkr.ecr.eu-west-2.amazonaws.com/velociraptor:latest \
    ParameterKey=GtfsSourceUri,ParameterValue=s3://my-bucket/gtfs-2026-06-01.zip \
    ParameterKey=ContainerMemory,ParameterValue=8192 \
    ParameterKey=ContainerCpu,ParameterValue=2048 \
    ParameterKey=DesiredCount,ParameterValue=2 \
    ParameterKey=HealthCheckGracePeriod,ParameterValue=300
```

## Parameters

| Parameter | Description | Example | Required |
|---|---|---|---|
| `Environment` | Environment name, used to suffix every resource | `prod`, `staging`, `dev` | Yes |
| `VpcId` | Existing VPC id | `vpc-xxxxx` | Yes |
| `PrivateSubnetIds` | Private subnet ids, at least two AZs | `subnet-xxx,subnet-yyy` | Yes |
| `AlbListenerArn` | ALB HTTPS listener ARN | `arn:aws:elasticloadbalancing:...` | Yes |
| `AlbTestListenerArn` | Optional test listener for blue/green validation | `arn:aws:elasticloadbalancing:...` | No |
| `ContainerImage` | ECR image URI | `123456789012.dkr.ecr.eu-west-2.amazonaws.com/velociraptor:latest` | Yes |
| `GtfsSourceUri` | S3 URI of the GTFS feed | `s3://bucket/gtfs-2026-06-01.zip` | Yes |
| `ContainerMemory` | Task memory (MB) | `8192` | Yes |
| `ContainerCpu` | Task CPU units | `2048` | Yes |
| `DesiredCount` | Number of tasks | `2` | Yes |
| `HealthCheckGracePeriod` | Seconds allowed for precompute before health checks bite | `300` | Yes |

Memory and grace period both scale with the feed: precompute builds a `RaptorAlgorithm` for every
service date at startup, so a feed covering more dates needs more of both.

## What gets created

- **ECS cluster** and **service** — `velociraptor-{env}`, CodeDeploy controlled
- **Task definition** — `velociraptor-{env}`, revision increments on update
- **Target groups** — `velociraptor-blue-{env}`, `velociraptor-green-{env}`
- **Security groups** — task SG and ALB-to-task SG
- **IAM roles** — task role (S3 read), execution role (ECR and logs), CodeDeploy role
- **CodeDeploy application** and **deployment group** — `velociraptor-{env}`
- **CloudWatch log group** — `/ecs/velociraptor-{env}`

## Publishing a new feed

1. Point the stack at the new GTFS object:

   ```bash
   aws cloudformation update-stack \
     --stack-name velociraptor-prod \
     --use-previous-template \
     --capabilities CAPABILITY_NAMED_IAM \
     --parameters \
       ParameterKey=GtfsSourceUri,ParameterValue=s3://my-bucket/gtfs-2026-06-08.zip \
       ParameterKey=Environment,UsePreviousValue=true \
       ParameterKey=VpcId,UsePreviousValue=true \
       ParameterKey=PrivateSubnetIds,UsePreviousValue=true \
       ParameterKey=AlbListenerArn,UsePreviousValue=true \
       ParameterKey=ContainerImage,UsePreviousValue=true \
       ParameterKey=ContainerMemory,UsePreviousValue=true \
       ParameterKey=ContainerCpu,UsePreviousValue=true \
       ParameterKey=DesiredCount,UsePreviousValue=true \
       ParameterKey=HealthCheckGracePeriod,UsePreviousValue=true
   ```

2. Trigger the CodeDeploy release:

   ```bash
   TASK_DEF_ARN=$(aws cloudformation describe-stacks \
     --stack-name velociraptor-prod \
     --query 'Stacks[0].Outputs[?OutputKey==`TaskDefinitionArn`].OutputValue' \
     --output text)

   cat > appspec.json <<EOF
   {
     "version": 0.0,
     "Resources": [{
       "TargetService": {
         "Type": "AWS::ECS::Service",
         "Properties": {
           "TaskDefinition": "$TASK_DEF_ARN",
           "LoadBalancerInfo": {
             "ContainerName": "velociraptor",
             "ContainerPort": 8080
           }
         }
       }
     }]
   }
   EOF

   aws deploy create-deployment \
     --application-name velociraptor-prod \
     --deployment-group-name velociraptor-prod \
     --revision '{"revisionType":"AppSpecContent","appSpecContent":{"content":"'"$(cat appspec.json)"'"}}'
   ```

## Blue/green flow

1. The stack update produces a new task definition revision.
2. CodeDeploy starts a green task set: new tasks download the feed from S3 and precompute. Health
   checks pass only once precompute finishes.
3. The ALB shifts traffic to the green target group. Blue tasks drain in-flight requests for five
   minutes.
4. Blue tasks terminate; the blue target group is retained for rollback.

## Monitoring and rollback

```bash
aws deploy get-deployment --deployment-id d-XXXXXXXXX

aws ecs describe-services \
  --cluster velociraptor-prod --services velociraptor-prod \
  --query 'services[0].events[0:10]'

aws logs tail /ecs/velociraptor-prod --follow
```

To abort a deployment that is failing health checks:

```bash
aws deploy stop-deployment --deployment-id d-XXXXXXXXX --auto-rollback-enabled
```

CodeDeploy reverts to the blue task set.

## Troubleshooting

**Stack creation fails on `AlbListenerRule` with "A target group ARN must be specified".** The ALB
listener ARN must exist and be valid:

```bash
aws elbv2 describe-listeners --listener-arns arn:aws:elasticloadbalancing:...
```

**Tasks fail health checks.** Check `aws logs tail /ecs/velociraptor-prod --follow` for S3 access
denied (task role permissions), `OutOfMemoryError` (raise `ContainerMemory`), or precompute running
past the grace period (raise `HealthCheckGracePeriod`).

**CodeDeploy stuck waiting for traffic to route to the replacement task set.** Check target health:

```bash
aws elbv2 describe-target-health \
  --target-group-arn $(aws cloudformation describe-stacks \
    --stack-name velociraptor-prod \
    --query 'Stacks[0].Outputs[?OutputKey==`TargetGroupGreenArn`].OutputValue' \
    --output text)
```

Targets should be `healthy`. If not, confirm that `/q/health/ready` on port 9000 returns 200, that
the security group allows ALB to task on port 8080, and that precompute has completed.

## Cost

Indicative Fargate pricing for two tasks at 2 vCPU and 8 GB, running continuously in `eu-west-2`:

- vCPU: 2 × 2 × $0.04156/vCPU-hour ≈ $0.166/hour
- Memory: 2 × 8 × $0.004551/GB-hour ≈ $0.073/hour
- **Roughly $170/month**, plus CloudWatch Logs (~$0.50/GB ingested) and S3 storage for the feeds.

Rates vary by region and change over time — check current pricing rather than relying on these
figures.
