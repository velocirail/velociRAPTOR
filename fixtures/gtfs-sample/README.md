# The sample feed

An artificial GTFS feed over a real corner of the network. Stations, CRS codes, operators and
geography are real so that queries read like the ones the service answers; every time, calendar and
fixed link is invented, so nothing here is derived from a licensed extract and the feed can ship
with the repository.

It is committed in three shapes. The network and the timetable are the same in each; what differs is how
the feed says it:

| directory | shape |
|---|---|
| `fixtures/gtfs-sample-gb-transit` | as gb-transit publishes `gtfs-national-rail-only.zip` |
| `fixtures/gtfs-sample-gb-transit-rail-and-tfl` | as gb-transit publishes `gtfs-rail-and-tfl.zip` |
| `fixtures/gtfs-sample` (this directory) | the deprecated dtd2gtfs format |

The feed is generated, not edited. `SampleFeed` in `gtfs/src/test/java/.../gtfs/sample` is the
source of truth and `SampleFeedGoldenTest` fails when this directory drifts from it. To change the
network, edit the generator and run

```
./mvnw -pl gtfs test -Dtest=SampleFeedGoldenTest -Dsample.feed.update=true
```

then read the diff before committing it.

## The dtd2gtfs shape (deprecated)

This directory is in the dtd2gtfs format, which is deprecated and will be removed with it:

| file | notes |
|---|---|
| `stops.txt` | `stop_id` is the CRS code, `stop_code` the TIPLOC, `stop_desc` the CATE interchange status |
| `trips.txt` | `trip_headsign` carries the train UID; `trip_id` is numeric and distinct from it |
| `stop_times.txt` | times run past `24:00:00` on the night services; request stops have pickup and drop-off type `3` |
| `transfers.txt` | one self-row per station giving the minimum interchange in seconds; three request stops have none |
| `links.txt` | this project's fixed-link format: one row per window, direction and day pattern, with a mode |
| `calendar_dates.txt` | removals only (`exception_type` 2), as gb-transit publishes them |
| `feed_info.txt` | the window the feed is complete for: 2026-06-01 to 2026-06-28 |

## The gb-transit shapes

`gtfs-sample-gb-transit` is written as gb-transit writes a feed, so the reader is tested against what it will be
given:

| file | notes |
|---|---|
| `stops.txt` | stations are `910G` + TIPLOC with `location_type` 1 and the CRS in `stop_code`; every call is at a boarding point beneath one, `9100` + TIPLOC + platform (`9100BRGHTN5`), or `9100` + TIPLOC where the call names no platform |
| `trips.txt` | `trip_id` is the UID and the schedule's first and last dates (`WB0900_20260601_20260628`); `trip_headsign` is the destination |
| `agency.txt`, `routes.txt` | `agency_id` is the National Operator Code form (`=SN`); `route_id` is the ATOC code |
| `transfers.txt` | a self-row per station for the interchange; one row per pair of stations for each fixed link, with gb-transit's `mode`, window, date and day columns; and one split/join row (`transfer_type` 4), a stand-in coupling of the 10:00 fast from VIC and the 11:25 West Coastway at BTN |
| `areas.txt`, `stop_areas.txt` | London Terminals as a group station, which the reader leaves unread |

A fixed link is published as gb-transit publishes one: the envelope of the pair's records, so the Tube and the
ferry, which have a shorter Sunday window, are open for the Monday to Saturday hours every day. That is the one
way the journeys differ from the dtd2gtfs shape's (`BUGS.md` §10.1).

`gtfs-sample-gb-transit-rail-and-tfl` is the same without the Tube links, and with a Victoria line in their place:

| station | as |
|---|---|
| Victoria, Euston, King's Cross St. Pancras | platforms (`9400ZZLUVIC1`) beneath the rail stations VIC, EUS and STP, so they route as those stations |
| Green Park `100`, Oxford Circus `101`, Warren Street `102` | TfL stations of their own, under the codes gb-transit gives them |

Trains run every ten minutes from 05:30 to 24:30 each way, daily, ten minutes end to end. Warren Street has a
five minute walk to Euston with no window of its own. BTN to MKC changes at VIC onto the Victoria line and at
EUS onto London Northwestern.

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

In the dtd2gtfs shape `trip_id` is the operator digit, the two-digit pattern index and the departure hhmm, so the
09:00 fast from BTN is `1020900`. The UID is the operator letter, the pattern letter and the hhmm: `WB0900`, and
in the gb-transit shapes it leads the `trip_id`: `WB0900_20260601_20260628`. None shifts when a train is added.

| operator | digit | UID letter | patterns |
|---|---|---|---|
| SN | 1 | W | 01/A fast down, 02/B fast up, 03/C stop down, 04/D stop up, 05/E owl down, 06/F owl up, 07/G overlay, 08/H west out, 09/J west back, 10/K east out, 11/L east back, 12/M Marshlink out, 13/N Marshlink back |
| TL | 2 | L | 01/A down, 02/B up |
| GX | 3 | G | 01/A down, 02/B up |
| SW | 4 | S | 01/A out, 02/B back |
| LM | 5 | M | 01/A out, 02/B back |

"Down" is away from London.
