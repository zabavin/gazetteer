package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Map;

public class Constants {
	public static final Map<String, Integer> defaultType2size = new HashMap<>();
	
	static {
		defaultType2size.put("letter", 8);
		defaultType2size.put("street", 10);
		defaultType2size.put("hn", 20);
		
		defaultType2size.put("place:quarter", 30);
		defaultType2size.put("place:neighbourhood", 40);
		defaultType2size.put("place:suburb", 50);
		defaultType2size.put("place:allotments", 60);
		defaultType2size.put("place:locality", 70);
		defaultType2size.put("place:isolated_dwelling", 70);
		defaultType2size.put("place:village", 70);
		defaultType2size.put("place:hamlet", 70);
		defaultType2size.put("place:town", 70);
		defaultType2size.put("place:city", 70);

		defaultType2size.put("boundary:8", 80);
		defaultType2size.put("boundary:6", 90);
		defaultType2size.put("boundary:5", 100);
		defaultType2size.put("boundary:4", 110);
		defaultType2size.put("boundary:3", 120);
		defaultType2size.put("boundary:2", 130);
	}
}