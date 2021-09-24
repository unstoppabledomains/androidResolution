package com.unstoppabledomains.resolution.dns;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.unstoppabledomains.exceptions.dns.DnsException;
import com.unstoppabledomains.exceptions.dns.DnsExceptionCode;
import com.unstoppabledomains.exceptions.ns.NSExceptionParams;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DnsUtils {
  
  public static final int DEFAULT_TTL = 300;
  public List<DnsRecord> toList(Map<String, String> rawRecords) throws DnsException {
    List<DnsRecord> dnsRecords = new ArrayList<>();
    List<DnsRecordsType> types = getAllDnsTypes(rawRecords);
    for (DnsRecordsType type: types) {
      List<DnsRecord> dnsType = constructDnsRecords(rawRecords, type);
      dnsRecords.addAll(dnsType);
    }
    return dnsRecords;
  }

  public Map<String, String> toMap(List<DnsRecord> records) throws DnsException {
    Map<String, String> map = new HashMap<>();
    for (DnsRecord record: records) {
      DnsRecordsType type = record.getType();
      String ttlInRecord = map.get("dns." + type + ".ttl");
      JsonArray dnsInRecord = getJsonArray(map.get("dns." + type), type);
      if (dnsInRecord != null) {
        dnsInRecord.add(record.getData());
        map.put("dns." + type, dnsInRecord.toString());
      } else {
        map.put("dns." + type, "["+record.getData()+"]");
        map.put("dns." + type + ".ttl", Integer.toString(record.getTtl()));
      }

      if (ttlInRecord != null && !ttlInRecord.isEmpty() && !ttlInRecord.equals(Integer.toString(record.getTtl()))) {
        throw new DnsException(DnsExceptionCode.InconsistentTtl, new NSExceptionParams("r", type.toString()));
      }
    }
    return map;
  }

  private JsonArray getJsonArray(String jsonArray, DnsRecordsType type) throws DnsException {
    try {
      if (StringUtils.isBlank(jsonArray)) {
        return null;
      }
      return JsonParser.parseString(jsonArray).getAsJsonArray();
      
    } catch(JsonSyntaxException exception) {
      throw new DnsException(DnsExceptionCode.DnsRecordCorrupted,
        new NSExceptionParams("r", type.toString()));
    }
  }

  private List<DnsRecordsType> getAllDnsTypes(Map<String, String> rawRecords) {
    Set<DnsRecordsType> dnsTypes = new HashSet<>();
    for (Map.Entry<String, String> entry : rawRecords.entrySet()) {
      String[] chunks = entry.getKey().split("\\.");
      boolean isDnsType = chunks[0].equals("dns") && !chunks[1].equals("ttl");
      if (isDnsType) {
        dnsTypes.add(DnsRecordsType.valueOf(chunks[1]));
      }
    }
    return new ArrayList<>(dnsTypes);
  }

  private List<DnsRecord> constructDnsRecords(Map<String, String> rawRecords, DnsRecordsType type) throws DnsException {
    int ttl = parseTtl(rawRecords, type);
    String jsonValueString = rawRecords.get("dns." + type.toString());
    if (StringUtils.isEmpty(jsonValueString)) {
        return null;
    }
    List<DnsRecord> data = new ArrayList<>();
      JsonArray arr = getJsonArray(jsonValueString, type);
      for (JsonElement elem: arr) {
        String value = elem.getAsString();
        if (!StringUtils.isEmpty(value)) {
          data.add(new DnsRecord(type, ttl, value));
        }
      }
      return data;
  }

  private int parseTtl(Map<String, String> rawRecords, DnsRecordsType type) {
    int recordTtl = parseIfNumber(rawRecords.get("dns." + type.toString() + ".ttl"));
    if (recordTtl != -1) {
      return recordTtl;
    }
    int defaultRecordTtl = parseIfNumber(rawRecords.get("dns.ttl"));
    if (defaultRecordTtl != -1) {
      return defaultRecordTtl;
    }

    return DEFAULT_TTL;
  }

  private int parseIfNumber(String str) {
    if (StringUtils.isEmpty(str)) {
      return -1;
    }
    try {
      return(Integer.parseInt(str));
    } catch(NumberFormatException exception) {
      return -1;
    }
  }
}
