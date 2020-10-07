package com.unstoppabledomains.resolution.contracts;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.FastHex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class Contract {

  private String address;
  private String url;
  private JsonArray abi;

  public Contract(String url, String address, String pathToAbi) {
    this.address = address;
    this.url = url;
    this.abi = getAbi(pathToAbi);
  }
  
  protected <T> T fetchOne(String method, Object[] args) throws IOException {
    Tuple answ = fetchMethod(method, args);
    try {
      return (T) answ.get(0);
    } catch (ArrayIndexOutOfBoundsException e) {
      return null;
    }
  }

  private JsonArray getAbi(String pathToAbi) {
      String jsonString;
      try {
          jsonString = new String(Files.readAllBytes(Paths.get(pathToAbi)));
      } catch (IOException e) {
          throw new RuntimeException("Couldn't find an ABI for " + getClass().getSimpleName() + " contract", e);
      }
      return new JsonParser().parse(jsonString).getAsJsonArray();
  }

  private Tuple fetchMethod(String method, Object[] args) throws IOException {
    JsonObject methodDescription = getMethodDescription(method, args.length);
    if (methodDescription == null) {
      throw new IOException("Couldn't found method from ABI");
    }
    Function function = Function.fromJson(methodDescription.toString());
    ByteBuffer encoded = function.encodeCallWithArgs(args);
    String data = toHexString(encoded.array());
    JsonArray params = prepareParamsForBody(data, address);
    JsonObject body = HTTPUtil.prepareBody("eth_call", params);
    JsonObject response = HTTPUtil.post(url, body);
    String answer = response.get("result").getAsString();
    if (isUnknownError(answer)) {
      return new Tuple();
    }
    final String replacedAnswer = answer.replace("0x", "");
    return function.decodeReturn(FastHex.decode(replacedAnswer));
  }

  protected String fetchAddress(String method, Object[] args) throws IOException {
    BigInteger address = fetchOne(method, args);
    if (address == null) {
      return null;
    }
    return "0x" + address.toString(16);
  }

  private String toHexString(byte[] input) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("0x");
    for (byte b : input) {
      stringBuilder.append(String.format("%02x", b & 0xFF));
    }
    return stringBuilder.toString();
  }

  private JsonObject getMethodDescription(String method, int argLen) {
    JsonObject methodDescription;
    for (int i = 0; i < abi.size(); i++) {
      JsonObject m = (JsonObject) abi.get(i);
      JsonArray inputs = (JsonArray) m.get("inputs");
      JsonElement jname = m.get("name");
      if (jname == null) {
        continue;
      }
      String name = jname.getAsString();
      if (name.equals(method) && inputs.size() == argLen) {
        methodDescription = m;
        return methodDescription;
      }
    }
    return null;
  }

  private JsonArray prepareParamsForBody(String data, String address) {
    JsonObject jo = new JsonObject();
    jo.addProperty("data", data);
    jo.addProperty("to", address);
    JsonArray params = new JsonArray();
    params.add(jo);
    params.add("latest");
    return params;
  }

  private boolean isUnknownError(String answer) {
    return "0x".equals(answer);
  }
}
