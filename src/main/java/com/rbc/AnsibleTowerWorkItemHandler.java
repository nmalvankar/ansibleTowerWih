/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rbc;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.drools.core.util.StringUtils;
import org.jbpm.process.workitem.core.AbstractLogOrThrowWorkItemHandler;
import org.jbpm.process.workitem.core.util.RequiredParameterValidator;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jbpm.process.workitem.core.util.Wid;
import org.jbpm.process.workitem.core.util.WidParameter;
import org.jbpm.process.workitem.core.util.WidResult;
import org.jbpm.process.workitem.core.util.service.WidAction;
import org.jbpm.process.workitem.core.util.service.WidAuth;
import org.jbpm.process.workitem.core.util.service.WidService;
import org.jbpm.process.workitem.core.util.WidMavenDepends;

@Wid(widfile="AnsibleTower.wid", name="AnsibleTower",
        displayName="AnsibleTower",
        defaultHandler="mvel: new com.rbc.AnsibleTowerWorkItemHandler()",
        documentation = "ansibleTowerWih/index.html",
        category = "ansibleTowerWih",
        icon = "AnsibleTower.png",
        parameters={
            @WidParameter(name="ansibleTowerUrl", required = true),
            @WidParameter(name="bearerToken", required = true),
            @WidParameter(name="contentData", required = false),
            @WidParameter(name="contentType", required = false)
        },
        results={
            @WidResult(name="result")
        },
        mavenDepends={
            @WidMavenDepends(group="com.rbc", artifact="ansibleTowerWih", version="1.0.0-SNAPSHOT")
        },
        serviceInfo = @WidService(category = "ansibleTowerWih", description = "${description}",
                keywords = "",
                action = @WidAction(title = "Sample Title"),
                authinfo = @WidAuth(required = true, params = {"ansibleTowerUrl", "bearerToken"},
                        paramsdescription = {"ansibleTowerUrl", "bearerToken"},
                        referencesite = "referenceSiteURL")
        )
)
public class AnsibleTowerWorkItemHandler extends AbstractLogOrThrowWorkItemHandler {

    private static final Logger logger = LoggerFactory.getLogger(AnsibleTowerWorkItemHandler.class);
    public static final String PARAM_STATUS = "Status";
    public static final String PARAM_STATUS_MSG = "StatusMsg";
    public static final String PARAM_RESULT = "Result";
    
    private String ansibleTowerUrl;
    private String bearerToken;

    private ClassLoader classLoader;
        

    public AnsibleTowerWorkItemHandler(String ansibleTowerUrl, String bearerToken){
            this.ansibleTowerUrl = ansibleTowerUrl;
            this.bearerToken = bearerToken;
        }

    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        try {
            RequiredParameterValidator.validate(this.getClass(), workItem);

            Map<String, Object> params = workItem.getParameters();

            // sample parameters
            ansibleTowerUrl = (String) workItem.getParameter("ansibleTowerUrl");
            bearerToken = (String) workItem.getParameter("bearerToken");

            Map<String, Object> results = new HashMap<String, Object>();

            this.classLoader = this.getClass().getClassLoader();
            
            //Rest API call to Ansible Tower URL
            Object content = params.get("contentData");
            String contentType = (String)params.get("contentType") != null ? (String)params.get("contentType") : "application/json";
            String resultClass = (String)params.get("resultClass");

            DefaultHttpClient httpClient = new DefaultHttpClient();
		    HttpPost postRequest = new HttpPost(ansibleTowerUrl);
            postRequest.addHeader("Authorization","Bearer " + bearerToken);
            postRequest.addHeader("Content-Type", contentType);

            if(content != null) {
                if (!(content instanceof String)) {
                    content = transformRequest(content, contentType);
                }
                StringEntity entity = new StringEntity((String) content, contentType);
                postRequest.setEntity(entity);   
            }

            HttpResponse response = httpClient.execute(postRequest);
            int responseCode = response.getStatusLine().getStatusCode();
            String responseBody = null;
            String responseContentType = null;

            if(response.getEntity() != null) {
                responseBody = EntityUtils.toString(response.getEntity());
            }

            if(response.getEntity().getContentType() != null)
                responseContentType = response.getEntity().getContentType().getValue();

            if(responseCode >= 200 && responseCode < 300) {
                postProcessResult(responseBody,
                                  resultClass,
                                  responseContentType,
                                  results);
                results.put(PARAM_STATUS_MSG,
                            "request to endpoint " + ansibleTowerUrl + " successfully completed " + response.getStatusLine().getReasonPhrase());
            }

            // return results
            results.put(PARAM_STATUS, responseCode);

            manager.completeWorkItem(workItem.getId(), results);
        } catch(Throwable cause) {
            handleException(cause);
        }
    }

    @Override
    public void abortWorkItem(WorkItem workItem,
                              WorkItemManager manager) {
        // stub
    }

    protected String transformRequest(Object data,
                                      String contentType) {
        try {
            if (contentType.toLowerCase().contains("application/json")) {
                ObjectMapper mapper = new ObjectMapper();

                return mapper.writeValueAsString(data);
            } else if (contentType.toLowerCase().contains("application/xml")) {
                StringWriter stringRep = new StringWriter();
                JAXBContext jaxbContext = JAXBContext.newInstance(new Class[]{data.getClass()});

                jaxbContext.createMarshaller().marshal(data,
                                                       stringRep);

                return stringRep.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to transform request to object",
                                       e);
        }
        throw new IllegalArgumentException("Unable to find transformer for content type '" + contentType + "' to handle data " + data);
    }

    protected void postProcessResult(String result,
    String resultClass,
    String contentType,
    Map<String, Object> results) {
        if (!StringUtils.isEmpty(resultClass) && !StringUtils.isEmpty(contentType)) {
        try {
        Class<?> clazz = Class.forName(resultClass,
                    true,
                    classLoader);

        Object resultObject = transformResult(clazz,
                            contentType,
                            result);

        results.put(PARAM_RESULT,
        resultObject);
        } catch (Throwable e) {
        throw new RuntimeException("Unable to transform respose to object",
                e);
        }
        } else {

        results.put(PARAM_RESULT,
        result);
        }
    }

    protected Object transformResult(Class<?> clazz,
    String contentType,
    String content) throws Exception {

if (contentType.toLowerCase().contains("application/json")) {
ObjectMapper mapper = new ObjectMapper();

return mapper.readValue(content,
   clazz);
} else if (contentType.toLowerCase().contains("application/xml")) {
StringReader result = new StringReader(content);
JAXBContext jaxbContext = JAXBContext.newInstance(new Class[]{clazz});

return jaxbContext.createUnmarshaller().unmarshal(result);
}
logger.warn("Unable to find transformer for content type '{}' to handle for content '{}'",
contentType,
content);
// unknown content type, returning string representation
return content;
}

}

