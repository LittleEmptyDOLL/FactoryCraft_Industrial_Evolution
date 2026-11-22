#!/usr/bin/env python3
"""Read-only native origin/FIFO/dual-lease restart receipts. Requires nbtlib."""

import argparse
import hashlib
import json
import uuid
from pathlib import Path

import nbtlib


def identity(values):
    return str(uuid.UUID(int=sum(
        (int(value) & 0xFFFFFFFF) << (96 - 32 * index)
        for index, value in enumerate(values)
    )))


def checkpoint(path):
    return nbtlib.load(path)["data"]["Checkpoint"]


def ledger(trains):
    cars = cargo = energy = spent = 0
    owners = set()
    for train in trains:
        for car in train["Cars"]:
            car_id = identity(car["Id"])
            assert car_id not in owners, "duplicate carriage owner"
            owners.add(car_id)
            cars += 1
            stacks = car["Items"]["Items"]
            if int(car["Kind"]) == 0:
                coal = sum(int(stack["count"]) for stack in stacks)
                energy += coal * 4_000_000 + int(car["FuelJ"]) + int(car["SpentJ"])
                spent += int(car["SpentJ"])
            else:
                cargo += sum(int(stack["count"]) for stack in stacks)
                assert all("components" in stack for stack in stacks)
    return dict(cars=cars, componentBearingCargo=cargo, coalBufferPlusSpentJ=energy, spentJ=spent)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("fifo_before", "fifo_after", "dual_before", "dual_after", "after_trains"):
        parser.add_argument(name)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    present = {identity(t["Id"]) for t in nbtlib.load(args.after_trains)["data"]["Trains"]}
    report = {}
    for name, count in (("fifo", 2), ("dual", 1)):
        before = checkpoint(getattr(args, name + "_before"))
        after = checkpoint(getattr(args, name + "_after"))
        assert int(before["Stage"]) == 1
        assert int(after["Stage"]) == 2 and bool(after["Replayed"])
        states = []
        for index in range(count):
            state = before["State" + str(index)]
            assert state == after["State" + str(index)], "initial expected snapshot changed"
            assert identity(state["Id"]) == identity(before["Train" + str(index)])
            assert identity(state["Id"]) not in present, "completed old owner resurrected"
            automation = state["Automation"]
            assert bool(automation["Automatic"])
            assert float(state["Speed"]) == 0
            assert float(automation["DepartureHead"]) == float(state["Head"])
            assert int(automation["DepartureDirection"]) in (-1, 1)
            assert int(automation["Entry"]) == 1
            states.append(state)
            if name == "fifo":
                assert identity(automation["Departure"]) == identity(before["Origin" + str(index)])
                assert not bool(automation["Routed"]) and "Destination" not in automation
                assert int(automation["DispatchOrder"]) > 0
            else:
                assert identity(automation["Departure"]) == identity(before["Origin"])
                assert identity(automation["Destination"]) == identity(before["Target"])
                assert bool(automation["Routed"]) and bool(after["RealCoalHandoff"])
        totals = ledger(states)
        if name == "fifo":
            assert totals["cars"] == 6 and totals["componentBearingCargo"] == 14
            assert totals["coalBufferPlusSpentJ"] == 128_000_000
            orders = [int(s["Automation"]["DispatchOrder"]) for s in states]
            assert len(set(orders)) == 2
            totals["savedReadyOrder"] = orders
        else:
            assert totals["cars"] == 3 and totals["componentBearingCargo"] == 7
            assert totals["coalBufferPlusSpentJ"] == 0 and totals["spentJ"] == 0
            totals["externalCoalJValidatedByNativeHandoffAndArrival"] = 4_000_000
        totals["nativeIndependentJvmReplayAndFiniteCompletion"] = True
        totals["retiredOwnersAbsent"] = True
        report[name] = totals
    report["sha256"] = {
        name: hashlib.sha256(Path(getattr(args, name)).read_bytes()).hexdigest()
        for name in ("fifo_before", "fifo_after", "dual_before", "dual_after", "after_trains")
    }
    Path(args.output).write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
