#!/usr/bin/env python

import os
import sys
import argparse
import logging
import re
import pandas


class Formatter(argparse.ArgumentDefaultsHelpFormatter,
                argparse.RawDescriptionHelpFormatter):
    pass


LOG_FORMAT = "%(asctime)-15s %(levelname)s %(relativeCreated)dms " \
             "%(filename)s::%(funcName)s():%(lineno)d %(message)s"

LOGGER = logging.getLogger(__name__)


LOGGER.info('Starting program')

def _parse_arguments(desc, args):
    """
    Parses command line arguments

    :param desc:
    :param args:
    :return:
    """
    parser = argparse.ArgumentParser(description=desc,
                                     formatter_class=Formatter)
    parser.add_argument('queryfile', help='File with result of psql '
                                          '-U XXX -d YYYY -c '
                                          '"select \"UUID\",name,nodecount,'
                                          'edgecount,'
                                          'creation_time,modification_time,'
                                          'owner,ndexdoi,'
                                          'REGEXP_REPLACE('
                                          'array_to_string(warnings,'
                                          '\':::\'),\'\n\',\'<NEWLINE>\') '
                                          'from network where iscomplete '
                                          'and is_deleted=false and '
                                          'visibility=\'PUBLIC\' and '
                                          'solr_idx_lvl=\'ALL\';"')
    parser.add_argument('outfile', help='The resulting tab delimited report will'
                                        'be written to this file path')
    parser.add_argument('--logconf', default=None,
                        help='Path to python logging configuration file in '
                             'this format: '
                             'https://docs.python.org/3/library/'
                             'logging.config.html#logging-config-fileformat'
                             '. Setting this overrides -v parameter '
                             'which uses default logger.')
    parser.add_argument('--verbose', '-v', action='count', default=0,
                        help='Increases verbosity of logger to standard '
                             'error for log messages '
                             'in this module and in. Messages are output '
                             'at these python logging levels -v = ERROR, '
                             '-vv = WARNING, -vvv = INFO, '
                             '-vvvv = DEBUG, -vvvvv = NOTSET')

    return parser.parse_args(args)


def _setup_logging(args):
    """
    Sets up logging based on parsed command line arguments.
    If args.logconf is set use that configuration otherwise look
    at args.verbose and set logging for this module and the one
    in ndexutil specified by TSV2NICECXMODULE constant
    :param args: parsed command line arguments from argparse
    :raises AttributeError: If args is None or args.logconf is None
    :return: None
    """

    if args is None or args.logconf is None:
        level = (50 - (10 * args.verbose))
        logging.basicConfig(format=LOG_FORMAT,
                            level=level)
        LOGGER.setLevel(level)
        return

    # logconf was set use that file
    logging.config.fileConfig(args.logconf,
                              disable_existing_loggers=False)


def truncate_str(input_str, maxstr_len=512):
    """

    :param input_str:
    :return:
    """

    if input_str is None:
        return input_str
    if len(input_str) <= maxstr_len:
        return input_str
    return input_str[0:maxstr_len] + '...'


def main(args):
    """

    :param args:
    :return:
    """
    desc = """
    This script finds CX2 converter warnings and 
    outputs a tab delimited file with just those
    entries


    """
    theargs = _parse_arguments(desc, args[1:])

    # setup logging
    _setup_logging(theargs)

    query_file = os.path.abspath(theargs.queryfile)

    df = pandas.read_csv(query_file, sep='\t',
                         names=['UUID', 'name', 'nodecount', 'edgecount',
                                'creation_time', 'modification_time',
                                'owner', 'ndexdoi', 'warnings'],
                         converters={i: str for i in range(0, 9)})

    # keep only rows with CX2-CONVERTER: in warnings array
    df = df[df['warnings'].str.contains('CX2-CONVERTER:', na=False)]

    # iterate through those warnings
    # split the warnings by ::: and keep only
    # the entries that start with CX2-CONVERTER
    # by creating a new cx2warnings column
    cx2_warnings = []
    for index, row in df.iterrows():
        warn_split = row['warnings'].split(':::')

        cur_warn = ''
        add_delim = False
        for entry in warn_split:
            if not entry.startswith('CX2-CONVERTER:'):
                continue
            if add_delim is True:
                cur_warn += ':::'
            else:
                add_delim = True
            cur_warn += entry
        cx2_warnings.append(cur_warn)

    df.insert(9, 'cx2warnings', cx2_warnings)

    df.drop(columns='warnings', inplace=True)
    df.to_csv(theargs.outfile, sep='\t')


if __name__ == '__main__':  # pragma: no cover
    sys.exit(main(sys.argv))
