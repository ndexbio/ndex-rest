package org.ndexbio.rest;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimerTask;

import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.task.NdexServerQueue;

public class DatabaseBackupTask extends TimerTask {

	  //expressed in milliseconds
	  protected final static long fONCE_PER_DAY = 1000*60*60*24;

	  private final static int fFOUR_AM = 1;

	  protected static Date getTomorrowBackupTime(){
	    Calendar tomorrow = new GregorianCalendar();
	    tomorrow.add(Calendar.DATE, 1);
	    Calendar result = new GregorianCalendar(
	      tomorrow.get(Calendar.YEAR),
	      tomorrow.get(Calendar.MONTH),
	      tomorrow.get(Calendar.DATE),
	      fFOUR_AM,
	      0
	    );
	    return result.getTime();
	  }	
	
	@Override
	public void run() {
		Task task = new Task();
		
		task.setTaskType(TaskType.SYSTEM_DATABASE_BACKUP);
		NdexServerQueue.INSTANCE.addFirstSystemTask(task);

	}

}
