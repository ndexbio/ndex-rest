package org.ndexbio.task;

import java.io.IOException;
import java.sql.SQLException;

import org.ndexbio.model.exceptions.NdexException;

public interface NdexSystemTask {

	public void run () throws SQLException, NdexException, IOException;
}
