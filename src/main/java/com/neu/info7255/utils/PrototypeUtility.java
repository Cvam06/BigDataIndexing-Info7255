package com.neu.info7255.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import com.neu.info7255.controller.PrototypeController;
import com.neu.info7255.exceptions.InvalidInputException;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
public class PrototypeUtility {
	
	private JedisPool jedisPool;
	
	public void validateObject(JSONObject jsonObject) {

        //created json schema for the object
        JSONObject jsonSchema = new JSONObject(
                new JSONTokener(PrototypeController.class.getResourceAsStream("/PrototypeSchema.json")));

        Schema schema = SchemaLoader.load(jsonSchema);
        schema.validate(jsonObject);
    }
	
	public String getETag(JSONObject json) {

        String encoded=null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.toString().getBytes(StandardCharsets.UTF_8));
            encoded = Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "\""+encoded+"\"";
    }

    public boolean verifyETag(JSONObject json, List<String> etags) {
        if(etags.isEmpty())
            return false;
        String encoded=getETag(json);
        return etags.contains(encoded);

    }
    
    public boolean isStringInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
