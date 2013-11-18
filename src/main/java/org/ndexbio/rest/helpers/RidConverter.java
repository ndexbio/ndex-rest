package org.ndexbio.rest.helpers;

import com.orientechnologies.orient.core.id.OClusterPositionFactory;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import org.ndexbio.rest.exceptions.ValidationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/******************************************************************************
* Converts between RIDs and JIDs. JIDs are used on the web site as a
* workaround for the limitations of limited characters that can be displayed
* in URLs. RIDs are the true IDs used by OrientDB.
******************************************************************************/
public class RidConverter
{
    public static ORID convertToRid(String Jid)
    {
        final Matcher m = Pattern.compile("^C(\\d*)R(\\d*)$").matcher(Jid.trim());

        if (m.matches())
            return new ORecordId(Integer.valueOf(m.group(1)), OClusterPositionFactory.INSTANCE.valueOf(m.group(2)));
        else
            throw new ValidationException(Jid + " is not valid JID.");
    }

    public static String convertToJid(ORID Rid)
    {
        return Rid.toString().replace("#", "C").replace(":", "R");
    }
}
