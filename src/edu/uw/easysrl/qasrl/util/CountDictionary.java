package edu.uw.easysrl.qasrl.util;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;

public class CountDictionary implements Serializable {
	TObjectIntHashMap<String> str2index;
	ArrayList<String> index2str;
	TIntArrayList index2count;
	
	public CountDictionary() {
		this.str2index = new TObjectIntHashMap<>();
		this.index2str = new ArrayList<>();
		this.index2count = new TIntArrayList();
	}
	
	// Copy from existing dictionary.
	public CountDictionary(CountDictionary dict) {
		this();
		for (int sid = 0; sid < dict.size(); sid ++) {
			String str = dict.getString(sid);
			index2str.add(str);
			index2count.add(dict.getCount(sid));
			str2index.put(str, sid);
		}
	}
	
	public CountDictionary(CountDictionary dict, int minFrequency) { 
		this();
		for (int sid = 0; sid < dict.size(); sid ++) {
			int freq = dict.getCount(sid);
			if (freq < minFrequency) {
				continue;
			}
			String str = dict.getString(sid);
			str2index.put(str, index2str.size());
			index2str.add(str);
			index2count.add(freq);
		}
	}
	
	public void clearCounts() {
		for (int sid = 0; sid < index2count.size(); sid ++) {
			index2count.set(sid, 0);
		}
	}
	
	public void insertTuple(int id, String str, int freq) {
		assert id == index2str.size();
		index2str.add(str);
		index2count.add(freq);
		str2index.put(str,id);
	}
 	
	public int addString(String str) {
		if (str2index.contains(str)) {
			int sid = str2index.get(str);
			int count = index2count.get(sid);
			index2count.set(sid, count + 1);
			return sid;
		} else {
			int sid = index2str.size();
			index2str.add(str);
			index2count.add(1);
			str2index.put(str, sid);
			return sid;
		}
	}
	
	public int addString(String str, boolean acceptNew) {
		if (str2index.contains(str)) {
			int sid = str2index.get(str);
			int count = index2count.get(sid);
			index2count.set(sid, count + 1);
			return sid;
		} else if (acceptNew) {
			int sid = index2str.size();
			index2str.add(str);
			index2count.add(1);
			str2index.put(str, sid);
			return sid;
		}
		return -1;
	}

	public int addString(String str, int count) {
		if (str2index.contains(str)) {
			int sid = str2index.get(str);
			index2count.set(sid, index2count.get(sid) + count);
			return sid;
		} else {
			int sid = index2str.size();
			index2str.add(str);
			index2count.add(count);
			str2index.put(str, sid);
			return sid;
		}
	}
	
	public int addString(String str, String unseenMarker) {
		return str2index.contains(str) ? addString(str) : addString(unseenMarker);
	}
	
	public boolean contains(String str) {
		return str2index.contains(str);
	}
	
	public int lookupString(String str) {
		if (!str2index.contains(str)) {
			return -1;
		}
		return str2index.get(str);
	}
	
	public int getCount(String str) {
		if (!str2index.contains(str)) {
			return 0;
		}
		return index2count.get(str2index.get(str));
	}
	
	public int getCount(int index) {
		return (index < index2count.size()) ? index2count.get(index) : 0; 
	}
	
	public int size() {
		return index2str.size();
	}
	
	// TODO: handle -1 index value.
	public String getString(int index) {
		return index2str.get(index);
	}
	
	public String[] getStringArray(int[] indices) {
		String[] strings = new String[indices.length];
		for (int i = 0; i < indices.length; i++) {
			strings[i] = getString(indices[i]);
		}
		return strings;
	}
	
	public ArrayList<String> getStrings() {
		return index2str;
	}
	
	public int getTotalCount() {
		int totalCount = 0;
		for (int i = 0; i < index2count.size(); i++) {
			totalCount += index2count.get(i);
		}
		return totalCount;
	}
	
	public void prettyPrint() {
		for (int i = 0; i < size(); i++) {
			System.out.println(String.format("%d\t%s\t%d", i, index2str.get(i), index2count.get(i)));
		}
	}

}