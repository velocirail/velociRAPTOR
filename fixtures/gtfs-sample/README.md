# The sample feed

An artificial GTFS feed over a real corner of the network. Stations, CRS codes, operators and
geography are real so that queries read like the ones the service answers; every time, calendar and
fixed link is invented, so nothing here is derived from a licensed extract and the feed can ship
with the repository.

The feed is generated, not edited. `SampleFeed` in `gtfs/src/test/java/.../gtfs/sample` is the
source of truth and `SampleFeedGoldenTest` fails when this directory drifts from it. To change the
network, edit the generator and run

```
./mvnw -pl gtfs test -Dtest=SampleFeedGoldenTest -Dsample.feed.update=true
```

then read the diff before committing it.

## Shape

The files are in the shape the bridge reads today (dtd2gtfs style):

| file | notes |
|---|---|
| `stops.txt` | `stop_id` is the CRS code, `stop_code` the TIPLOC, `stop_desc` the CATE interchange status |
| `trips.txt` | `trip_headsign` carries the train UID; `trip_id` is numeric and distinct from it |
| `stop_times.txt` | times run past `24:00:00` on the night services; request stops have pickup and drop-off type `3` |
| `transfers.txt` | one self-row per station giving the minimum interchange in seconds; three request stops have none |
| `links.txt` | this project's fixed-link format: one row per window, direction and day pattern, with a mode |
| `calendar_dates.txt` | removals only (`exception_type` 2), as gb-transit publishes them |
| `feed_info.txt` | the window the feed is complete for: 2026-06-01 to 2026-06-28 |

## The network

```
  MKC -- WFJ -- EUS                                   London Northwestern (LM)
                 :  TUBE / WALK between the termini
  STP -- ZFD -- LBG          VIC                      Thameslink (TL) / Southern (SN) / Gatwick Express (GX)
                  \          |
                  ECR ------ CLJ
                   |
                  RDH -- GTW -- TBD -- HHE
                                         |
  PMH -- PMS -- FTN -- HAV -- CCH -- WRH -- PLD -- HOV -- BTN -- LWS -- EBN -- BEX -- HGS -- ORE ..TOK..DLH..WSE.. RYE -- APD -- HMT -- AFK ~ ASI
   ~ FERRY                                 West Coastway            East Coastway          Marshlink
  RYP -- RYD -- SHN                                  Island Line (SW)
```

Trains run hourly from 06:00 to 22:00. "Even hours daily" means the xx:00 departures at even
hours run every day and the odd ones Monday to Saturday, which gives Sunday a thinner timetable.

| service | calls | time | pattern |
|---|---|---|---|
| SN fast | VIC ECR GTW HHE BTN | 60 min | xx:00 both ways; even hours daily, odd Mon-Sat |
| SN stopping | VIC CLJ ECR RDH GTW TBD HHE BTN | 85 min | xx:15, xx:45 from VIC; xx:10, xx:40 from BTN; Mon-Sat |
| SN night owl | VIC ECR GTW HHE BTN | 60 min | Saturday only, 24:15 from each end (00:15 Sunday). Set-down only at ECR southbound, pick-up only northbound |
| SN engineering overlay | as stopping | 85 min | Mon-Fri 22-26 June the 22:00 fast from VIC is withdrawn and a 22:05 stopper runs instead |
| GX | VIC GTW | 30 min | xx:30 from VIC, xx:35 from GTW, daily |
| TL | STP ZFD LBG ECR GTW TBD HHE BTN | 75 min | xx:10 from STP, xx:20 from BTN, daily |
| SN West Coastway | BTN HOV PLD WRH CCH HAV FTN PMS PMH | 70 min | xx:25 from BTN, xx:05 from PMH; even hours daily |
| SN East Coastway | BTN LWS EBN BEX HGS | 60 min | xx:33 from BTN, xx:05 from HGS; even hours daily |
| SN Marshlink | HGS ORE TOK DLH WSE RYE APD HMT AFK | 55 min | xx:45 from HGS, xx:05 from AFK; even hours daily. TOK, DLH, WSE are request stops |
| SW Island Line | RYP RYD SHN | 25 min | xx:20 from RYP, xx:50 from SHN, daily |
| LM | EUS WFJ MKC | 35 min | xx:50 from EUS, xx:10 from MKC, daily |

Fixed links:

| link | mode | duration | window |
|---|---|---|---|
| VIC, LBG, STP, EUS, each pair both ways | TUBE | 15 to 25 min | Mon-Sat 05:30-24:30, Sun 07:00-23:30 |
| STP - EUS | WALK | 12 min | always |
| PMH - RYP | FERRY | 22 min | Mon-Sat 06:00-23:00, Sun 08:00-20:00 |
| AFK - ASI | WALK | 5 min | always. ASI has no trains: it is reachable by the link alone |

Calendar: 2026-06-01 (Monday) to 2026-06-28 (Sunday). 2026-06-15 is a bank holiday: the
Monday-to-Saturday services are removed, leaving the daily (Sunday-pattern) timetable.

## Journeys the feed is built to exercise

- **Fast versus stopper.** BTN to VIC 08:30 to 09:30 on a weekday returns the 09:00 fast, the
  09:10 stopper, and the 09:20 Thameslink with a change at ECR onto that same stopper (it overtakes it,
  so the change departs later for the same arrival); the 08:40 stopper reaches VIC after the fast and is
  dominated.
- **Interchange timing.** The Marshlink from AFK reaches HGS at xx:00 for a coastway departing xx:05 with a
  4 minute interchange, so that connection is made by one minute. The coastway from PMH reaches BTN at xx:15
  for a Thameslink departing xx:20 with a 5 minute interchange, made exactly.
- **Not-via.** Every train from BTN to VIC calls at GTW, so `notVia=GTW` returns nothing, while
  `notVia=CLJ` removes only the stoppers.
- **Cross-London.** BTN to MKC is fast to VIC, Tube to EUS, LM onwards: a train, link, train
  journey that is the only way there.
- **Rules.** EUS to BTN cannot begin with the Tube and HGS to ASI cannot end with the walk, so
  those return nothing under the default fixed-link rules.
- **After midnight.** A Saturday 23:30 to Sunday 01:00 window over VIC to BTN returns the owl,
  serialised as 00:15 on the Sunday.
- **Day patterns.** The ferry has a later Sunday start. Link windows are checked against the arrival at the
  far end, interchange included: the 06:50 from SHN reaches RYP at 07:15 and would be at PMH at 07:42, inside
  Saturday's window and before Sunday's 08:00 start, so SHN to BTN 06:00 to 07:00 has one journey on Saturday
  and none on Sunday.
- **Calendar exceptions.** BTN to VIC 09:30 to 10:15 returns two journeys on a weekday and one on
  the bank holiday; the 22:00 fast from VIC is absent on 24 June and the 22:05 stopper present.
- **Request stops.** HGS to DLH alights with drop-off type `COORDINATE_WITH_DRIVER`.

## Identifiers

`trip_id` is the operator digit, the two-digit pattern index and the departure hhmm, so the 09:00
fast from BTN is `1020900`. The UID is the operator letter, the pattern letter and the hhmm:
`WB0900`. Neither shifts when a train is added.

| operator | digit | UID letter | patterns |
|---|---|---|---|
| SN | 1 | W | 01/A fast down, 02/B fast up, 03/C stop down, 04/D stop up, 05/E owl down, 06/F owl up, 07/G overlay, 08/H west out, 09/J west back, 10/K east out, 11/L east back, 12/M Marshlink out, 13/N Marshlink back |
| TL | 2 | L | 01/A down, 02/B up |
| GX | 3 | G | 01/A down, 02/B up |
| SW | 4 | S | 01/A out, 02/B back |
| LM | 5 | M | 01/A out, 02/B back |

"Down" is away from London.
