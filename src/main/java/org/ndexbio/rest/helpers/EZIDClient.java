package org.ndexbio.rest.helpers;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** 
 * This class is created from the example of ezid API document. 
 * 
 * @author jingchen
 *
 */
class EZIDClient {

    static String SERVER = "https://ezid.cdlib.org";

    static class MyAuthenticator extends Authenticator {

        private String USERNAME;
        private String PASSWORD;

    	public MyAuthenticator(String username, String password) {
    	 this.USERNAME = username;
    	 this.PASSWORD = password;
    	}
    	
        @Override
		protected PasswordAuthentication getPasswordAuthentication () {
            return new PasswordAuthentication(
                USERNAME, PASSWORD.toCharArray());
        }
    }

    static class Response {

        int responseCode;
        String status;
        String statusLineRemainder;
        HashMap<String, String> metadata;

        public String toString () {
            StringBuffer b = new StringBuffer();
            b.append("responseCode=");
            b.append(responseCode);
            b.append("\nstatus=");
            b.append(status);
            b.append("\nstatusLineRemainder=");
            b.append(statusLineRemainder);
            b.append("\nmetadata");
            if (metadata != null) {
                b.append(" follows\n");
                Iterator<Map.Entry<String, String>> i =
                    metadata.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String, String> e = i.next();
                    b.append(e.getKey() + ": " + e.getValue() + "\n");
                }
            } else {
                b.append("=null\n");
            }
            return b.toString();
        }

    }

    static String encode (String s) {
        return s.replace("%", "%25").replace("\n", "%0A").
            replace("\r", "%0D").replace(":", "%3A");
    }

    static String toAnvl (HashMap<String, String> metadata) {
        Iterator<Map.Entry<String, String>> i =
            metadata.entrySet().iterator();
        StringBuffer b = new StringBuffer();
        while (i.hasNext()) {
            Map.Entry<String, String> e = i.next();
            b.append(encode(e.getKey()) + ": " +
                     encode(e.getValue()) + "\n");
        }
        return b.toString();
    }

    static String decode (String s) {
        StringBuffer b = new StringBuffer();
        int i;
        while ((i = s.indexOf("%")) >= 0) {
            b.append(s.substring(0, i));
            b.append((char)
                     Integer.parseInt(s.substring(i+1, i+3), 16));
            s = s.substring(i+3);
        }
        b.append(s);
        return b.toString();
    }

    static String[] parseAnvlLine (String line) {
        String[] kv = line.split(":", 2);
        kv[0] = decode(kv[0]).trim();
        kv[1] = decode(kv[1]).trim();
        return kv;
    }

    static Response issueRequest (
        String method, String path, HashMap<String, String> metadata)
        throws Exception {
        HttpURLConnection c = (HttpURLConnection)
            (new URL(SERVER + "/" + path)).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "text/plain");
        if (metadata != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type",
                                 "text/plain; charset=UTF-8");
            OutputStreamWriter w =
                new OutputStreamWriter(c.getOutputStream(), "UTF-8");
            w.write(toAnvl(metadata));
            w.flush();
        }
        Response r = new Response();
        r.responseCode = c.getResponseCode();
        InputStream is = r.responseCode < 400? c.getInputStream() :
            c.getErrorStream();
        if (is != null) {
            BufferedReader br = new BufferedReader(
                new InputStreamReader(is, "UTF-8"));
            String[] kv = parseAnvlLine(br.readLine());
            r.status = kv[0];
            r.statusLineRemainder = kv[1];
            HashMap<String, String> d = new HashMap<String, String>();
            String l;
            while ((l = br.readLine()) != null) {
                kv = parseAnvlLine(l);
                d.put(kv[0], kv[1]);
            }
            if (d.size() > 0) r.metadata = d;
        }
        return r;
    }
    
    public static String createDOI (String URL, String creator, String title, String prefix) throws Exception {
        // Sample POST request.
        System.out.println("Issuing POST request...");
        HashMap<String, String> metadata =
            new HashMap<>();
        metadata.put("_target", URL);
        metadata.put("datacite.creator", creator);
        metadata.put("datacite.title", title);
        metadata.put("datacite.publisher", "Network Data Exchange (NDEx)");
        metadata.put("datacite.publicationyear", "2021");
        metadata.put("datacite.resourcetype", "Dataset");
        
        //String prefix = "10.18119/N9";
        //String prefix = "10.5072/FK2";  //apitest
        
        Response r = issueRequest( "POST", "shoulder/doi:" + prefix, metadata);
        
        Pattern p = Pattern.compile("^doi:(\\S+) .*$");
        Matcher m = p.matcher(r.statusLineRemainder);
        if (!m.matches()) {
        	throw new Exception ("Invalid DOI format in the status line:" + r.statusLineRemainder);
        }
        String id = m.group(1);
        System.out.print(id);
        return id;
    }

  /*  public static Response deleteDOI (String doi) throws Exception {
    	HashMap<String, String> metadata =
                new HashMap<>();
    	Response r = issueRequest(
                "DELETE", "/id/doi:" + doi, metadata);
            System.out.print(r);
            return r;    	
    }  */
     
    public static void main (String[] args) throws Exception {

        Authenticator.setDefault(new MyAuthenticator("apitest","apitest"));

        String r = createDOI("https://www.ndexbio.org", "ucsd dev", "NDEx Home Page", "10.5072/FK2");

        // Sample GET request.
        System.out.println("\nIssuing GET request...");
        String id = r;
        Response r1 = issueRequest("GET", "id/" + URLEncoder.encode("doi:"+id, "UTF-8"),
                         null);
        System.out.print(r1); 

    }

}
