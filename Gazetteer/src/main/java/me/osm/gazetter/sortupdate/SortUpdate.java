package me.osm.gazetter.sortupdate;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetter.join.Joiner;

public class SortUpdate {
	
	private static final Logger log = LoggerFactory.getLogger(SortUpdate.class);
	private String dataDir;
	
	public SortUpdate(String dataDir) {
		this.dataDir = dataDir;
	}
	
	public void run() {
		ExecutorService executorService = Executors.newFixedThreadPool(4);
		
		for(File stripeF : new File(dataDir).listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
			executorService.execute( new SortAndUpdateTask(stripeF));
		}
		
		executorService.shutdown();
		
		try {
			executorService.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Execution service shutdown awaiting interrupted.", e);
		}
		
		log.info("Update slices done. {} lines was updated.", SortAndUpdateTask.countUpdatedLines());
	}
}