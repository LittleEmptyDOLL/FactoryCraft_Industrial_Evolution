#!/usr/bin/env python3
"""Summarize statistical JFR allocation/CPU samples inside a benchmark's measured interval."""
import argparse
import collections
import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess


def instant(value):
    return datetime.datetime.fromisoformat(value.replace('Z', '+00:00'))


def summarize(report, recording, executable):
    start, end = instant(report['measurementStartedUtc']), instant(report['measurementEndedUtc'])
    result = subprocess.run([executable, 'print', '--json', '--events',
                             'jdk.ObjectAllocationSample,jdk.ExecutionSample', str(recording)],
                            check=True, stdout=subprocess.PIPE, text=True)
    events = json.loads(result.stdout)['recording']['events']
    classes, frames, cpu = collections.Counter(), collections.Counter(), collections.Counter()
    weight, samples = 0, 0
    for event in events:
        value = event['values']
        if not start <= instant(value['startTime']) <= end:
            continue
        stack = (value.get('stackTrace') or {}).get('frames', [])
        factory = next((f['method']['type']['name'].replace('/', '.') + '.' + f['method']['name']
                        for f in stack if f['method']['type']['name'].startswith('factorycraft')), None)
        if event['type'] == 'jdk.ObjectAllocationSample':
            allocation = value['weight']
            weight += allocation
            classes[value['objectClass']['name'].replace('/', '.')] += allocation
            if factory:
                frames[factory] += allocation
        else:
            samples += 1
            if factory:
                cpu[factory] += 1
    return {'scope': 'Statistical JFR samples within the report interval; allocation weights are estimates, not exact object counts.',
            'estimatedAllocationBytes': weight, 'executionSamples': samples,
            'allocationClasses': classes.most_common(15),
            'firstFactoryAllocationFrames': frames.most_common(15),
            'firstFactoryExecutionFrames': cpu.most_common(15)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--jfr', type=Path, help='Recording path; defaults to <profile>-profile.jfr beside the report.')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    recording = args.jfr or args.report.with_name(report['profile'].lower() + '-profile.jfr')
    java_root = os.environ.get('JAVA_HOME')
    executable = str(Path(java_root) / 'bin' / ('jfr.exe' if os.name == 'nt' else 'jfr')) if java_root else shutil.which('jfr')
    if not executable or not Path(executable).is_file():
        parser.error('Set JAVA_HOME to a JDK 21 installation containing the jfr tool.')
    output = json.dumps(summarize(report, recording, executable), indent=2) + '\n'
    if args.output:
        args.output.write_text(output)
        print('Written', args.output)
    else:
        print(output, end='')


if __name__ == '__main__':
    main()
