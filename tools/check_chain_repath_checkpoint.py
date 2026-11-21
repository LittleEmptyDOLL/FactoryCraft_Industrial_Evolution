#!/usr/bin/env python3
"""Read-only independent-JVM chain-detour receipt. Requires nbtlib; never edits worlds."""

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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("after_trains")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    before = nbtlib.load(args.before)["data"]["Checkpoint"]
    after = nbtlib.load(args.after)["data"]["Checkpoint"]
    actual = nbtlib.load(args.after_trains)["data"]["Trains"]
    assert int(before["Stage"]) == 1
    assert int(after["Stage"]) == 2 and bool(after["Replayed"])
    present = {identity(train["Id"]) for train in actual}
    cargo = energy = cars = 0
    car_ids = set()
    for key, state in (("Train", "State"), ("Parked", "ParkedState")):
        train = before[state]
        assert train == after[state], "immutable expected initial state changed"
        assert identity(train["Id"]) == identity(before[key])
        assert identity(train["Id"]) not in present, "retired old owner resurrected"
        for car in train["Cars"]:
            car_id = identity(car["Id"])
            assert car_id not in car_ids, "duplicate car owner"
            car_ids.add(car_id)
            cars += 1
            stacks = car["Items"]["Items"]
            if int(car["Kind"]) == 0:
                coal = sum(int(stack["count"]) for stack in stacks)
                energy += coal * 4_000_000 + int(car["FuelJ"]) + int(car["SpentJ"])
            else:
                cargo += sum(int(stack["count"]) for stack in stacks)
                assert all("components" in stack for stack in stacks)
    automation = before["State"]["Automation"]
    wait = int(automation["SignalWaitTicks"])
    assert bool(automation["Automatic"]) and bool(automation["Routed"])
    assert int(before["State"]["Direction"]) == 1
    assert float(before["State"]["Speed"]) == 0
    assert 20 <= wait < 100
    assert identity(automation["HeldSignal"]) == identity(before["Chain"])
    assert identity(automation["Destination"]) == identity(before["Original"])
    assert cars == 4 and cargo == 7 and energy == 96_000_000
    report = dict(
        initialTrain=identity(before["Train"]),
        initialDestination=identity(before["Original"]),
        alternateDestination=identity(before["Alternate"]),
        savedWaitTicks=wait,
        cars=cars,
        componentBearingCargo=cargo,
        coalBufferPlusSpentJ=energy,
        exactIndependentJvmReplayAndNativeDetourCompletion=True,
        retiredOwnersAbsent=True,
        sha256={name: hashlib.sha256(Path(getattr(args, name)).read_bytes()).hexdigest()
                for name in ("before", "after", "after_trains")},
    )
    Path(args.output).write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
