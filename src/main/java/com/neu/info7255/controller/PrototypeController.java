package com.neu.info7255.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;

import com.neu.info7255.utils.AuthorizeService;
import com.neu.info7255.utils.PrototypeServices;
import com.neu.info7255.utils.PrototypeUtility;

import org.everit.json.schema.ValidationException;
import org.json.*;

@RestController
@RequestMapping("/")
public class PrototypeController {
	
	@Autowired
	PrototypeUtility utils;
	
	private JedisPool jedisPool;
	
	@Autowired
	PrototypeServices prototypeServices;
	
	@Autowired
	AuthorizeService authorizeService;
	
	@Autowired
	PrototypeUtility prototypeUtility;
	
	@GetMapping("/test")
	public String Hello() {
		return "Hello World";
	}
	
	// Service AuthorizeService dependent che
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, value = "/token")
    public ResponseEntity getToken(){

        String token;
        try {
            token = authorizeService.generateToken();
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new JSONObject().put("token", token).toString());

    }
	
	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, value = "/plan")
    public ResponseEntity createPlan(@Valid @RequestBody(required = false) String jsonData, @RequestHeader HttpHeaders requestHeaders) throws URISyntaxException {
		
		String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }
        
        if (jsonData == null || jsonData.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Request body is Empty. Kindly provide the JSON").toString());
        }

        JSONObject jsonPlan = new JSONObject(new JSONTokener(jsonData));

        try {
            utils.validateObject(jsonPlan);
        } catch(ValidationException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error",ex.getMessage()).toString());
        }

        if(this.prototypeServices.checkIfKeyExists(jsonPlan.get("objectType") + ":" + jsonPlan.get("objectId"))){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new JSONObject().put("message", "Plan Already Exists").toString());
        }
        
        String key = jsonPlan.get("objectType") + ":" + jsonPlan.get("objectId");
        String etag = this.prototypeServices.savePlan(jsonPlan, key);
        JSONObject response = new JSONObject();
        response.put("objectId", jsonPlan.get("objectId"));
        response.put("message", "Plan Created Successfully!!");

        return ResponseEntity.created(new URI("/plan/"+key)).eTag(etag)
                .body(response.toString());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, value = "/{objectType}/{objectID}")
    public ResponseEntity getPlan(@PathVariable String objectID, @PathVariable String objectType, @RequestHeader HttpHeaders requestHeaders){
    	
    	String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        
        System.out.println("Result = "+result);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }
        
        String key = objectType + ":" + objectID;
        if(!this.prototypeServices.checkIfKeyExists(key)){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Sorry, No Object Found").toString());
        } else {
        	
        	String ifNotMatch;
            try{
                ifNotMatch = requestHeaders.getFirst("If-None-Match");
            } catch (Exception e){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                        body(new JSONObject().put("error", "ETag value is invalid! If-None-Match value should be string.").toString());
            }
            
            if(objectType.equals("plan")){
                String actualEtag = prototypeServices.getEtag(key);
                if (ifNotMatch != null && ifNotMatch.equals(actualEtag)) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(actualEtag).build();
                }
            }
            
            Map<String, Object> plan = prototypeServices.getPlan(key);

            if (objectType.equals("plan")) {
                String actualEtag = prototypeServices.getEtag(key);
                return ResponseEntity.ok().eTag(actualEtag).body(new JSONObject(plan).toString());
            }

            return ResponseEntity.ok().body(new JSONObject(plan).toString());
        }
    }
    
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE, value = "/{objectType}/{objectID}")
    public ResponseEntity deletePlan(@RequestHeader HttpHeaders requestHeaders,
                                        @PathVariable String objectID,  @PathVariable String objectType){

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }

        String key = objectType + ":" + objectID;
        if(!prototypeServices.checkIfKeyExists(key)){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exists!!").toString());
        }


        String actualEtag = prototypeServices.getEtag(key);
        String eTag = requestHeaders.getFirst("If-Match");
        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag not provided in request!!").toString());
        }
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).build();
        }

        prototypeServices.deletePlan(key);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/plan/{objectID}")
    public ResponseEntity updatePlan( @RequestHeader HttpHeaders requestHeaders, @Valid @RequestBody(required = false) String jsonData,
                                             @PathVariable String objectID) throws IOException {

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }

        if (jsonData == null || jsonData.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Request body is Empty. Kindly provide the JSON").toString());
        }

        JSONObject jsonPlan = new JSONObject(jsonData);
        String key = "plan:" + objectID;

        if(!prototypeServices.checkIfKeyExists(key)){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exists!!").toString());
        }

        String actualEtag = prototypeServices.getEtag(key);
        String eTag = requestHeaders.getFirst("If-Match");
        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag not provided in request!!").toString());
        }
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag).build();
        }

        try {
            utils.validateObject(jsonPlan);
        } catch(ValidationException ex){
        	return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error",ex.getCausingExceptions()).toString());
        }

        prototypeServices.deletePlan(key);
        String newEtag = prototypeServices.savePlan(jsonPlan, key);

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put("message: ", "Resource updated successfully!!").toString());
    }
    
    @PatchMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/plan/{objectID}")
    public ResponseEntity<Object> patchPlan(@RequestHeader HttpHeaders requestHeaders, @Valid @RequestBody(required = false) String jsonData,
                                            @PathVariable String objectID) throws IOException {

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }

        if (jsonData == null || jsonData.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Request body is Empty. Kindly provide the JSON").toString());
        }

        JSONObject jsonPlan = new JSONObject(jsonData);
        String key = "plan:" + objectID;
        if (!prototypeServices.checkIfKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exists!!").toString());
        }

        String actualEtag = prototypeServices.getEtag(key);
        String eTag = requestHeaders.getFirst("If-Match");
        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag not provided in request!!").toString());
        }
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag)
                    .body(new JSONObject().put("message", "Plan has been updated by another user!!").toString());
        }
        
        String newEtag =  prototypeServices.savePlan(jsonPlan, key);

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put("message: ", "Resource updated successfully!!").toString());
    }

}
