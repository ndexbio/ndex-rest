import re
import psycopg2
import sys
import datetime
import os
import json

#save a record to db.
def save_rec(tid, rec, cursor, record_cnt, connection):
    if rec['agent'] == 'Zoho Monitor' or rec['agent'] == 'Site24x7'\
            or rec['user'] == 'dexterpratt' or rec['user'] == 'cjtest'\
            or rec['user'] == 'drh' or rec['user'] == 'scratch'\
            or rec['user'] == 'vrynkov':
        return record_cnt

    sql = """INSERT INTO request_record_raw(tid,start_time,end_time, ip, method, user_name, auth_type, user_agent, function_name,
            path, query_params, path_params, data, status, error)
                  VALUES(%s,%s,%s,%s,%s,%s,%s, %s,%s,%s,%s,%s,%s,%s,%s);"""

  #  print(rec)
    tstart = datetime.datetime.strptime(rec['start'], "%Y-%m-%d %H:%M:%S,%f")
    tend = datetime.datetime.strptime(rec['end'], "%Y-%m-%d %H:%M:%S,%f")
    datastr = None
    if 'data' in rec:
        datastr = rec['data']
    err = None
    if 'err' in rec:
        err = rec['err'].replace("\x00", "")
    cursor.execute(sql, (tid, tstart, tend, rec['ip'], rec['method'], rec['user'], rec['auth'],
                         rec['agent'],rec['fun'], rec['path'],rec['q_param'],rec['p_param'], datastr,
                         rec['status'], err))
    record_cnt += 1
    if (record_cnt % 600) == 0:
        connection.commit()

    return record_cnt


def load_log_file_to_db (file_name, holder):
    p_start = re.compile('^\\[(.*)\\]\t\\[tid:(.*)\\]\t\\[start\\]\t\\[(\\w+)\\]' +
                         '\t\\[(.*)\\]\t\\[(.*)\\]\t\\[(.*)\\]\t\\[(.*)\\]' + # up to function name
                         '\t\\[(.*)\\]\t\\[(\\{.*\\})\\]\t\\[(\\{.*\\})\\]')
    p_data = re.compile('^\\[(.*)\\]\t\\[tid:(.*)\\]\t\\[data\\]\t(.*)')
    p_end = re.compile('^\\[(.*)\\]\t\\[tid:(.*)\\]\t\\[end\\]\t\\[\\w+\\]\t\\[status: (\\d+)\\](\t\\[error: (.*)\\])?')

    p_auth = re.compile('^(G|B):(\w+)')


    # Define our connection string
    conn_string = "host='xxx' port='5432' dbname='xxxx' user='xxxx' password='xxx'"

    with open(file_name) as fp:
        line = fp.readline()
        cnt = 1
        rec_cnt = 0
        try:
            conn = psycopg2.connect(conn_string)
        except:
            e = sys.exc_info()[0]
            print("Error: %s" % e)
            sys.exit()

        cur = conn.cursor()

        while line:
            # print("Line {}: {}".format(cnt, line.strip()))
            line.strip()

            m = p_start.match(line)
            if m:
                t1 = m.group(1)
                tid = m.group(2)
                method = m.group(3)
                austr = m.group(4)
                auth = None
                user = None
                if austr:
                    mp = p_auth.match(austr)
                    if mp:
                        auth = mp.group(1)
                        user = mp.group(2)
                    else:
                        user = austr
                path_p_raw = m.group(10)
                rid = None
                if len(path_p_raw) > 2:
                    p_obj = json.loads(path_p_raw)
                    if len(p_obj)>0:
                        if len(p_obj) == 1:
                            rid = next(iter(p_obj.values()))
                        else:
                            RuntimeError("Error: record " + tid + " -- path parameter has more than 1 values")

                holder[tid] = {'start': t1,
                           'method': method,
                           'auth': auth,
                           'user': user,
                           'ip': m.group(5),
                           'agent': m.group(6),
                           'fun': m.group(7),
                           'path': m.group(8),
                           'q_param': m.group(9),
                           'p_param': rid}
            else:
                m2 = p_data.match(line)
                if m2:
                    tid_data = m2.group(2)
                    rec = holder[tid_data]
                    if rec:
                        rec['data'] = m2.group(3)
                    else:
                        holder[tid_data] = {'data': m2.group(3)}
                else:
                    m3 = p_end.match(line)
                    if m3:
                        t2 = m3.group(1)
                        tid_e = m3.group(2)
                        if tid_e in holder:
                            rec = holder[tid_e]

                            rec['end'] = t2
                            rec['status'] = m3.group(3)
                            errormsg = m3.group(5)
                            if errormsg:
                                rec['err'] = errormsg

                            #process the record
                            rec_cnt = save_rec(tid_e, rec, cur, rec_cnt, conn)
                            del holder[tid_e]
                        else:
                            RuntimeError ("Warning: .... end log for tid:" + tid_e + " appears before start.")

                    else:
                        RuntimeError("Unparsable line " + str(cnt) + ": " + line)

            line = fp.readline()
            cnt += 1

        conn.commit()
        cur.close()
        conn.close()
        print("Total record added : " + str(rec_cnt))





rec_holder = {}


# uncomment this part to debug error on a specific file
#filepath = '/Users/chenjing/working/prod_log/ndex-2017-03-31.0.log'
#load_log_file_to_db(filepath, rec_holder)


dir_name = '/Users/chenjing/working/prod_log'
for fname in sorted(os.listdir(dir_name)):
    print (dir_name+"/"+fname)
    load_log_file_to_db(dir_name+"/"+fname, rec_holder)

