package org.ndexbio.rest.helpers;

import com.orientechnologies.orient.core.id.OClusterPositionFactory;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/******************************************************************************
* Converts between RIDs and JIDs. JIDs are used on the web site as a
* workaround for the limitations of limited characters that can be displayed
* in URLs. RIDs are the true IDs used by OrientDB.
******************************************************************************/
public class RidConverter
{
    /**************************************************************************
    * Converts a JID to a RID.
    * 
    * @param Jid
    *            The JID.
    * @throws IllegalArgumentException
    *            The JID isn't a valid JID.
    * @return The JID converted to a RID.
    **************************************************************************/
    public static ORID convertToRid(String Jid) throws IllegalArgumentException
    {
        final Matcher m = Pattern.compile("^C(\\d*)R(\\d*)$").matcher(Jid.trim());

        if (m.matches())
            return new ORecordId(Integer.valueOf(m.group(1)), OClusterPositionFactory.INSTANCE.valueOf(m.group(2)));
        else
            throw new IllegalArgumentException(Jid + " is not a valid JID.");
    }

    /**************************************************************************
    * Converts a RID to a JID.
    * 
    * @param Rid
    *            The RID.
    * @return The RID converted to a JID.
    **************************************************************************/
    public static String convertToJid(ORID Rid)
    {
        return Rid.toString().replace("#", "C").replace(":", "R");
    }
}
