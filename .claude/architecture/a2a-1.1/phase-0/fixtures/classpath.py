#!/usr/bin/env python3
"""Read an existing Surefire report's runtime classpath. Does not run Maven."""
import argparse,pathlib,xml.etree.ElementTree as E
p=argparse.ArgumentParser();p.add_argument('repo');p.add_argument('--version',choices=['1.0','0.3'],required=True);p.add_argument('--output',required=True);a=p.parse_args()
module=('compat-0.3/' if a.version=='0.3' else '')+'spec-grpc'
report=next((pathlib.Path(a.repo)/module/'target/surefire-reports').glob('TEST-*.xml'))
props=E.parse(report).getroot().find('properties');cp=next(x.attrib['value'] for x in props if x.attrib['name']=='java.class.path')
pathlib.Path(a.output).write_text(cp+'\n');print('Runtime classpath from',report)
