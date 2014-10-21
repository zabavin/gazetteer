package me.osm.gazetter.out;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import me.osm.gazetter.Options;
import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;

import com.google.code.externalsorting.ExternalSort;

public class CSVOutWriter implements LineHandler {
	
	public static final Map<String, String> ARG_TO_TYPE = new LinkedHashMap<>();
	static {
		ARG_TO_TYPE.put("address", FeatureTypes.ADDR_POINT_FTYPE);
		ARG_TO_TYPE.put("street", FeatureTypes.HIGHWAY_FEATURE_TYPE);
		ARG_TO_TYPE.put("place", FeatureTypes.PLACE_POINT_FTYPE);
		ARG_TO_TYPE.put("poi", FeatureTypes.POI_FTYPE);
		ARG_TO_TYPE.put("boundaries", FeatureTypes.ADMIN_BOUNDARY_FTYPE);
	}
	
	public static Comparator<String> defaultcomparator;

	private String dataDir;
	private List<List<String>> columns;
	private Set<String> types;
	
	private Map<String, CsvListWriter> writers = new HashMap<>();
	
	private PrintWriter out;
	
	private FeatureValueExtractor featureEXT = new FeatureValueExctractorImpl();
	private FeatureValueExtractor poiEXT;
	private AddrRowValueExtractor addrRowEXT = new AddrRowValueExctractorImpl();
	
	private Set<String> addrRowKeys = new HashSet<String>(addrRowEXT.getSupportedKeys());
	private Set<String> allSupportedKeys = new HashSet<String>(featureEXT.getSupportedKeys());

	private OSMDocFacade osmDocFacade;
	private DOCReader reader;
	
	private CSVOutLineHandler outLineHandler = null;

	private LinkedHashSet<String> orderedTypes;
	
	private int uuidColumnIndex = -1;

	public CSVOutWriter(String dataDir, String columns, List<String> types, String out, 
			String poiCatalog) {
		
		allSupportedKeys.addAll(addrRowKeys);
		
		this.dataDir = dataDir;
		this.columns = parseColumns(columns);
		this.types = new HashSet<>();
		this.orderedTypes = new LinkedHashSet<>();
		
		checkColumnsKeys();
		
		//XXX: Actualy rather shity place
		int i = 0;
		for(List<String> bc : this.columns) {
			for(String key : bc) {
				if(key.equals("uid")) {
					uuidColumnIndex = i;
				}
			}
			i++;
		}
		
		if(this.uuidColumnIndex < 0) {
			defaultcomparator = new Comparator<String>() {
				@Override
				public int compare(String r1, String r2) {
					return r1.compareTo(r2);
				}
			};
		}
		else {
			defaultcomparator = new Comparator<String>() {
				@Override
				public int compare(String r1, String r2) {
					String uid1 = StringUtils.split(r1, '\t')[uuidColumnIndex];
					String uid2 = StringUtils.split(r2, '\t')[uuidColumnIndex];
					
					return uid1.compareTo(uid2);
				}
			};
		}
		
		try {
			for(String type : types) {
				
				String ftype = ARG_TO_TYPE.get(type);
				this.types.add(ftype);
				this.orderedTypes.add(ftype);
				writers.put(ftype, new CsvListWriter(
						FileUtils.getPrintwriter(getFile4Ftype(ftype), false), 
						new CsvPreference.Builder('$', '\t', "\n").build()));
			}

			if("-".equals(out)) {
				this.out = new PrintWriter(new OutputStreamWriter(System.out, "UTF8"));
			}
			else {
				this.out = FileUtils.getPrintwriter(new File(out), false);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		if(poiCatalog.endsWith(".xml") || poiCatalog.equals("jar")) {
			reader = new DOCFileReader(poiCatalog);
		}
		else {
			reader = new DOCFolderReader(poiCatalog);
		}
		
		osmDocFacade = new OSMDocFacade(reader, null);
		
		poiEXT = new PoiValueExctractorImpl(osmDocFacade);
		
		outLineHandler = Options.get().getCsvOutLineHandler();
	}

	private void checkColumnsKeys() {
		boolean flag = false;
		for(List<String> c : this.columns) {
			for(String key : c) {
				if(featureEXT != null && !featureEXT.supports(key) 
						&& poiEXT != null && !poiEXT.supports(key) 
						&& addrRowEXT != null && !addrRowEXT.supports(key)) {
					System.err.println("Column key " + key + " is not supported.");
					flag = true;
				}  
			}
		}
		
		if(flag) {
			System.exit(1);
		}
	}

	private File getFile4Ftype(String ftype) {
		return new File(this.dataDir + "/" + ftype + ".csv.tmp");
	}

	private List<List<String>> parseColumns(String columns) {
		
		StringTokenizer tokenizer = new StringTokenizer(columns, " ,;[]", true);
		List<List<String>> result = new ArrayList<>();
		
		boolean inner = false;
		List<String> innerList = new ArrayList<>();
		
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if(!" ".equals(token) && !",".equals(token) && !";".equals(token)) {
				if("[".equals(token)) {
					inner = true;
				}
				else if("]".equals(token)) {
					inner = false;
					result.add(innerList);
					innerList = new ArrayList<>();
				}
				else {
					if(inner) {
						innerList.add(token);
					}
					else {
						result.add(Arrays.asList(token));
					}
				}
			}
		}
		
		if(!innerList.isEmpty()) {
			result.add(innerList);
		}
		return result;
	}

	public void write() {
		File folder = new File(dataDir);
		try {
			boolean containsBoundaries = types.remove(FeatureTypes.ADMIN_BOUNDARY_FTYPE);
			
			if(!types.isEmpty()) {
				for(File stripeF : folder.listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
					FileUtils.handleLines(stripeF, this);
				}
			}
			
			if(containsBoundaries) {
				types.add(FeatureTypes.ADMIN_BOUNDARY_FTYPE);
			}
			
			if(types.contains(FeatureTypes.ADMIN_BOUNDARY_FTYPE)) {
				FileUtils.handleLines(FileUtils.withGz(new File(dataDir + "/binx.gjson")), new LineHandler() {
					
					@Override
					public void handle(String s) {
						if(s != null) {
							JSONObject jsonObject = new JSONObject(s);
							JSONObject boundaries = jsonObject.optJSONObject("boundaries");
							if(boundaries != null) {
								Map<String, JSONObject> mapLevels = mapLevels(boundaries);
								List<Object> row = new ArrayList<>();
								
								for (List<String> column : columns) {
									row.add(getColumn(FeatureTypes.ADMIN_BOUNDARY_FTYPE, jsonObject, mapLevels, boundaries, column, null));
								}
								
								if(outLineHandler != null) {
									if(outLineHandler.handle(row, FeatureTypes.ADMIN_BOUNDARY_FTYPE, jsonObject, mapLevels, boundaries, null)) {
										writeNext(row, FeatureTypes.ADMIN_BOUNDARY_FTYPE);
									}
								}
								else {
									writeNext(row, FeatureTypes.ADMIN_BOUNDARY_FTYPE);
								}
								
							}
						}
					}
					
				});
			}
			
			for(CsvListWriter w : writers.values()) {
				w.flush();
				w.close();
			}
			
			out();
			
			out.flush();
			out.close();		
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	private void out() throws IOException {
		for(String type : this.orderedTypes) {
			final Set<String> ids = new HashSet<String>();
			FileUtils.handleLines(getFile4Ftype(type), new LineHandler() {
				@Override
				public void handle(String s) {
					if(uuidColumnIndex >= 0) {
						String uid = StringUtils.split(s, '\t')[uuidColumnIndex];
						if(ids.add(uid)) {
							out.println(s);
						}
					}
					else {
						out.println(s);					
					}
				}
			});
			getFile4Ftype(type).delete();
		}		
	}

	@Override
	public void handle(String line) {
		if(line == null) {
			return;
		}
		
		String ftype = GeoJsonWriter.getFtype(line);
		
		if(types.contains(ftype) && !FeatureTypes.ADMIN_BOUNDARY_FTYPE.equals(ftype)) {

			JSONObject jsonObject = new JSONObject(line);

			if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
				JSONArray addresses = jsonObject.optJSONArray("addresses");
				if(addresses != null) {
					for(int ri = 0; ri < addresses.length(); ri++ ) {
						List<Object> row = new ArrayList<>();
						JSONObject addrRow = addresses.getJSONObject(ri);
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						for (List<String> column : columns) {
							row.add(getColumn(ftype, jsonObject, mapLevels, addrRow, column, ri));
						}
						
						if(outLineHandler != null) {
							if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, addrRow, ri)) {
								writeNext(row, ftype);
							}
						}
						else {
							writeNext(row, ftype);
						}
						
					}
				}
			}
			else if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
				JSONArray boundaries = jsonObject.optJSONArray("boundaries");
				if(boundaries != null) {
					for(int i = 0; i < boundaries.length(); i++) {
						JSONObject bs = boundaries.getJSONObject(i);
						Map<String, JSONObject> mapLevels = mapLevels(bs);
						List<Object> row = new ArrayList<>();
						
						for (List<String> column : columns) {
							row.add(getColumn(ftype, jsonObject, mapLevels, bs, column, i));
						}
						
						if(outLineHandler != null) {
							if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, bs, i)) {
								writeNext(row, ftype);
							}
						}
						else {
							writeNext(row, ftype);
						}
					}
				}
			}
			else if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
				JSONObject boundaries = jsonObject.optJSONObject("boundaries");
				if(boundaries != null) {
					Map<String, JSONObject> mapLevels = mapLevels(boundaries);
					List<Object> row = new ArrayList<>();
					
					for (List<String> column : columns) {
						row.add(getColumn(ftype, jsonObject, mapLevels, boundaries, column, null));
					}
					
					if(outLineHandler != null) {
						if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, boundaries, null)) {
							writeNext(row, ftype);
						}
					}
					else {
						writeNext(row, ftype);
					}
					
				}
			}
			else if(FeatureTypes.POI_FTYPE.equals(ftype)) {
				List<JSONObject> addresses = getPoiAddresses(jsonObject);
				if(addresses != null) {
					int ai = 0;
					for(JSONObject addrRow : addresses) {
						List<Object> row = new ArrayList<>();
						Map<String, JSONObject> mapLevels = mapLevels(addrRow);
						
						for (List<String> column : columns) {
							row.add(getColumn(ftype, jsonObject, mapLevels, addrRow, column, ai));
						}
						
						if(outLineHandler != null) {
							if(outLineHandler.handle(row, ftype, jsonObject, mapLevels, addrRow, ai)) {
								writeNext(row, ftype);
							}
						}
						else {
							writeNext(row, ftype);
						}
						
						ai++;
					}
				}
			}
			else {
				List<Object> row = new ArrayList<>();
				
				for (List<String> column : columns) {
					row.add(getColumn(ftype, jsonObject, null, null, column, null));
				}
				
				if(outLineHandler != null) {
					if(outLineHandler.handle(row, ftype, jsonObject, null, null, null)) {
						writeNext(row, ftype);
					}
				}
				else {
					writeNext(row, ftype);
				}
			}
			
		}
		
	}
	
	private List<JSONObject> getPoiAddresses(JSONObject poi) {
		
		List<JSONObject> result = new ArrayList<>();
		JSONObject joinedAddresses = poi.optJSONObject("joinedAddresses");
		if(joinedAddresses != null) {
			
			//"sameSource"
			if(getAddressesFromObj(result, joinedAddresses, "sameSource")) {
				return result;
			}
			
			//"contains"
			if(getAddressesFromCollection(result, joinedAddresses, "contains")) {
				return result;
			}

			//"shareBuildingWay"
			if(getAddressesFromCollection(result, joinedAddresses, "shareBuildingWay")) {
				return result;
			}

			//"nearestShareBuildingWay"
			if(getAddressesFromCollection(result, joinedAddresses, "nearestShareBuildingWay")) {
				return result;
			}

			//"nearest"
			if(getAddressesFromObj(result, joinedAddresses, "nearest")) {
				return result;
			}
			
		}
		
		return result;
	}

	private boolean getAddressesFromObj(List<JSONObject> result,
			JSONObject joinedAddresses, String key) {
		
		boolean founded = false;
		
		JSONObject ss = joinedAddresses.optJSONObject(key);
		if(ss != null) {
			JSONArray addresses = ss.optJSONArray("addresses");
			if(addresses != null) {
				for(int i = 0; i < addresses.length(); i++) {
					result.add(addresses.getJSONObject(i));
				}
				founded = true;
			}
		}
		
		return founded;
	}

	private boolean getAddressesFromCollection(List<JSONObject> result,
			JSONObject joinedAddresses, String key) {
		
		boolean founded = false;
		
		JSONArray contains = joinedAddresses.optJSONArray("contains");
		if(contains != null && contains.length() > 0) {
			
			for(int ci = 0; ci < contains.length(); ci++) {
				JSONObject co = contains.getJSONObject(ci);
				JSONArray addresses = co.optJSONArray("addresses");
				if(addresses != null) {
					for(int i = 0; i < addresses.length(); i++) {
						result.add(addresses.getJSONObject(i));
						founded = true;
					}
				}
			}
			
		}
		
		return founded;
	}

	private void writeNext(List<Object> row, String ftype) {
		try {
			writers.get(ftype).write(row);
		} catch (IOException e) {
			throw new RuntimeException("Can't write row: " + row, e);
		}
	}

	private Map<String, JSONObject> mapLevels(JSONObject addrRow) {
		try {
			Map<String, JSONObject> result = new HashMap<String, JSONObject>();
			
			JSONArray parts = addrRow.getJSONArray("parts"); 
			for(int i = 0; i < parts.length(); i++) {
				JSONObject part = parts.getJSONObject(i);
				result.put(part.getString("lvl"), part); 
			}
			
			return result;
		}
		catch (JSONException e) {
			return null;
		}
	}

	private Object getColumn(String ftype, JSONObject jsonObject, 
			Map<String, JSONObject> addrRowLevels, JSONObject addrRow, List<String> column, Integer ri) {
		for(String key : column) {

			Object value = null;
			if(addrRowKeys.contains(key)) {
				value = addrRowEXT.getValue(key, jsonObject, addrRowLevels, addrRow);
			}
			else {
				if(FeatureTypes.POI_FTYPE.equals(ftype)) {
					value = poiEXT.getValue(key, jsonObject, ri);
				}
				else {
					value = featureEXT.getValue(key, jsonObject, ri);
				}
			}
			
			if(value instanceof String) {
				value = StringUtils.stripToNull((String) value);
			}
			
			if(value != null) {
				return value;
			}
		}
		return null;
	}
	
}
