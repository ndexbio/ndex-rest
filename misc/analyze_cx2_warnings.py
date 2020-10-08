#!/usr/bin/env python

import os
import sys
import argparse
import logging
import re


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
                                          '"select warnings '
                                          'from network;"')
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


def main(args):
    """

    :param args:
    :return:
    """
    desc = """
    This script analyzes CX2 warnings messages and
    generates a report of all the types of warnings


    """
    theargs = _parse_arguments(desc, args[1:])

    # setup logging
    _setup_logging(theargs)

    query_file = os.path.abspath(theargs.queryfile)

    warning_data_raw = []

    with open(query_file, 'r') as f:
        for line in f:
            if not line.startswith(' {'):
                continue
            if line.startswith(' {}'):
                continue
            stripped_line = line.rstrip()
            nocurly = re.sub('}$', '', re.sub('^ {', '', stripped_line))
            nostart_end_quote = re.sub('"$', '', re.sub('^"', '', nocurly))
            w_elements = nostart_end_quote.split('","')
            for w_frag in w_elements:
                if not w_frag.startswith('CX2-CONVERTER: '):
                    continue
                warning_data_raw.append(w_frag)

    condensed_warnings = set()
    condensed_warnings_example = dict()
    warn_count_dict = dict()
    # now lets make a condensed version of the errors by
    # identifying the common errors and replacing ids and values
    # with XXX to make them the same

    for wline in warning_data_raw:
        res = None
        if wline.startswith('CX2-CONVERTER: Edge attribute id: '):
            res = re.sub('^(CX2-CONVERTER: Edge attribute id:) [0-9]+ (is named) \'.*\' (which is not allowed in CX spec.$)',
                         '\g<1> ### \g<2> \'XXX\' \g<3>', wline)
        elif wline.startswith('CX2-CONVERTER: Node attribute id:'):
            res = re.sub('^(CX2-CONVERTER: Node attribute id:) [0-9]+ (is named) \'.*\' (which is not allowed in CX spec.$)',
                         '\g<1> ### \g<2> \'XXX\' \g<3>', wline)
        elif wline == 'CX2-CONVERTER: CX2 network won\'t be generated on Cytoscape network collection.':
            res = wline
        elif wline == 'CX2-CONVERTER: CX2 network won\'t be generated on networks that have more than 20000000 edges.':
            res = wline
        elif wline.startswith('CX2-CONVERTER: Duplicate edges attribute on id:'):
            res = re.sub('^(CX2-CONVERTER: Duplicate edges attribute on id:) [0-9]+(. Attribute \').*(\' has value \().*(\) and \().*(\))',
                         '\g<1> ###\g<2>XXX\g<3>XXX\g<4>XXX\g<5>', wline)
        elif wline.startswith('CX2-CONVERTER: Value '):
            res = 'CX2-CONVERTER: Value XXX is not a valid string for XXX. NDEx is converting it to XXX from XXX: For input string: \"XXX\"'
        elif wline.startswith('CX2-CONVERTER: Failed to parse mapping string \'C\' in mapping'):
            res = 'CX2-CONVERTER: Failed to parse mapping string \'C\' in mapping XXX'
        elif wline.startswith('CX2-CONVERTER: Failed to parse mapping string \'N\' in mapping'):
            res = 'CX2-CONVERTER: Failed to parse mapping string \'N\' in mapping XXX'
        elif wline.startswith('CX2-CONVERTER: Failed to parse mapping string \'NW\' in mapping'):
            res = 'CX2-CONVERTER: Failed to parse mapping string \'NW\' in mapping XXX'
        elif wline.startswith('CX2-CONVERTER: Failed to parse mapping string \'1\' in mapping'):
            res = 'CX2-CONVERTER: Failed to parse mapping string \'1\' in mapping XXX'
        elif wline.startswith('CX2-CONVERTER: Duplicate nodes attribute on id:'):
            res = 'CX2-CONVERTER: Duplicate nodes attribute on id: ###. Attribute \'XXX\' has value (XXX) and (XXX)'
        elif wline.startswith('CX2-CONVERTER: Duplicated network attribute '):
            res = 'CX2-CONVERTER: Duplicated network attribute \'XXX\' found.'
        else:
            print('ERROR this line did not match anything: ' + wline)
        if res is None:
            continue
        if res not in warn_count_dict:
            warn_count_dict[res] = 0
            condensed_warnings_example[res] = wline
        warn_count_dict[res] += 1
        condensed_warnings.add(res)

    for x in condensed_warnings:
        print(str(warn_count_dict[x]) +
              ' occurrences of => ' +
              condensed_warnings_example[x] + '\n')


if __name__ == '__main__':  # pragma: no cover
    sys.exit(main(sys.argv))
