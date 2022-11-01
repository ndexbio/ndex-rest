#!/home/ndexbio-user/miniconda3/bin/python

import re
import psycopg2
import sys
import datetime
import os
import json
from datetime import timedelta
import glob
import shutil

#find out dates that we need to lode the logs
def dates_to_be_loaded(db_conn_string):
    conn = psycopg2.connect(db_conn_string)
    cur = conn.cursor()
    sqlstr = "select start_time from request_record_raw order by start_time desc limit 1"
    cur.execute(sqlstr)
    (col1,) = cur.fetchone()
    cur.close()
    conn.close()
    last_date = col1.date()
    today=datetime.date.today()
    candidate_list =[]
    while (last_date + timedelta(days=1)) < today:
        last_date = last_date+timedelta(days=1)
        candidate_list.append(last_date.isoformat())
    return candidate_list

#copy over log files
def copy_log_file(src_dir, dest_dir, date_string):
   src_str=src_dir+"/ndex-"+date_string+".*.log"
   for file in glob.glob(src_str):
      shutil.copy(file,dest_dir)


#save a record to db.
def save_rec(tid, rec, cursor, record_cnt, connection):
    ndex_developers = {'dexterpratt','cjtest','drh' ,'scratch','vrynkov','rudipillich', 'scratch3','scratch2', 'bsettle',\
                  'churas','dotasek', 'keiono', 'ndexbutler', 'ndextutorials', 'ccmi','barrydemchak','aarongary',\
                  'mikefidel', 'cc.zhang','cyndex2test','mikefidel.us@gmail.com','sol015','cbass', 'dylanfong.ut'}

    if rec['agent'] == 'Zoho Monitor' or rec['agent'] == 'Site24x7' or rec['agent'].startswith("check_http/v") or rec['user'] in ndex_developers:
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
        print ("Committed record: " + str(record_cnt))

    return record_cnt


def load_log_file_to_db (file_name, holder, conn_string):
    p_start = re.compile('^\\[(.*)\\]\t\\[tid:(.*)\\]\t\\[start\\]\t\\[(\\w+)\\]' +
                         '\t\\[(.*)\\]\t\\[(.*)\\]\t\\[(.*)\\]\t\\[(.*)\\]' + # up to function name
                         '\t\\[(.*)\\]\t\\[(\\{.*\\})\\]\t\\[(\\{.*\\})\\]')
    p_data = re.compile('^\\[(.*)\\]\t\\[tid:(.*)\\]\t\\[data\\]\t(.*)')
    p_end = re.compile('^\\[(.*)\\]\t\\[tid:(.*)\\]\t\\[end\\]\t(\\[\\w+\\]\t)?\\[status: (\\d+)\\](\t\\[error: (.*)\\])?')

    p_auth = re.compile('^(G|B):(\w+)')



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
                           'ip': m.group(5).split("\s+")[0],
                           'agent': m.group(6),
                           'fun': m.group(7),
                           'path': m.group(8),
                           'q_param': m.group(9),
                           'p_param': rid}
            else:
                m2 = p_data.match(line)
                if m2:
                    tid_data = m2.group(2)
                    if tid_data in holder:
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
                       #     tmp_funName = m3.group(3)
                            tmp_status = m3.group(4)
                            rec['status'] = tmp_status
                            errormsg = m3.group(6)
                            if errormsg:
                                rec['err'] = errormsg

                            #process the record
                            rec_cnt = save_rec(tid_e, rec, cur, rec_cnt, conn)
                            del holder[tid_e]
                        # else:
                        #    RuntimeError ("Warning: .... end log for tid:" + tid_e + " appears before start.")

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

# Define our connection string
conn_string = "host='localhost' port='5432' dbname='dbname' user='usr' password='pswd'"

dir_name = '/home/ndexbio-user/load_logs/logs'
src_dir='/data/ndex_prod_logs/logs'

#clean the working dir
files = glob.glob(dir_name+'/*')
for f in files:
    os.remove(f)

#copy over new log files
for new_date in dates_to_be_loaded(conn_string):
   copy_log_file(src_dir, dir_name, new_date)

#load the logs to db
for fname in sorted(os.listdir(dir_name)):
    print (dir_name+"/"+fname)
    load_log_file_to_db(dir_name+"/"+fname, rec_holder,conn_string)

#populate the download table if today is the first day of a month
if datetime.datetime.now().day == 1:
    conn = psycopg2.connect(conn_string)
    cur = conn.cursor()
    sqlstr = """insert into public_download_count (network_id, d_month, downloads, owner )
      select path_params::uuid, date_trunc('month', start_time) , count(*),n.owner 
      from  request_record_raw r left outer join  prod_network_full_view n on r.path_params::uuid = n.id
      where r.start_time >= (date_trunc('month',now() - interval '1 month'))  and r.start_time < date_trunc('month',now()) 
        and r.function_name in ('getCompleteNetworkAsCX','getCX2Network') and r.status = 200 
        and ((r.user_name is null) or r.user_name <> n.owner) and (n.visibility='PUBLIC' )
      group by path_params, date_trunc('month', start_time),n.owner
             """
    cur.execute(sqlstr)
    conn.commit()
    cur.close()
    conn.close()
