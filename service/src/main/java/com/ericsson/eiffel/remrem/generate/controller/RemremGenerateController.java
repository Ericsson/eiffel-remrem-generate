/*
    Copyright 2018 Ericsson AB.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.ericsson.eiffel.remrem.generate.controller;

import com.ericsson.eiffel.remrem.generate.config.ErLookUpConfig;
import com.ericsson.eiffel.remrem.generate.constants.RemremGenerateServiceConstants;
import com.ericsson.eiffel.remrem.generate.exception.REMGenerateException;
import com.ericsson.eiffel.remrem.protocol.MsgService;
import com.ericsson.eiffel.remrem.shared.VersionService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.classic.Logger;
import io.swagger.annotations.*;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/*")
@Api(value = "REMReM Generate Service", description = "REST API for generating Eiffel messages")
public class RemremGenerateController {

    Logger log = (Logger) LoggerFactory.getLogger(RemremGenerateController.class);

    // regular expression that exclude "swagger-ui.html" from request parameter
    private static final String REGEX = ":^(?!swagger-ui.html).*$";

    @Autowired
    private List<MsgService> msgServices;

    private JsonParser parser = new JsonParser();

    @Autowired
    private ErLookUpConfig erlookupConfig;

    private static ResponseEntity<String> response;

    private static RestTemplate restTemplate = new RestTemplate();

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Returns event information as json element based on the message protocol,
     * taking message type and json body as input.
     * <p>
     * <p>
     * Parameters: msgProtocol - The message protocol, which tells us which
     * service to invoke. msgType - The type of message that needs to be
     * generated. bodyJson - The content of the message which is used in
     * creating the event details.
     * <p>
     * Returns: The event information as a json element
     */
    @ApiOperation(value = "To generate eiffel event based on the message protocol", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Event sent successfully"),
            @ApiResponse(code = 400, message = "Malformed JSON"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Message protocol is invalid") })
    @RequestMapping(value = "/{mp" + REGEX + "}", method = RequestMethod.POST)
    public ResponseEntity<?> generate(
            @ApiParam(value = "message protocol", required = true) @PathVariable("mp") final String msgProtocol,
            @ApiParam(value = "message type", required = true) @RequestParam("msgType") final String msgType,
            @ApiParam(value = "ER lookup result multiple found, Generate will fail") @RequestParam(value = "failIfMultipleFound", required = false, defaultValue = "false") final Boolean failIfMultipleFound,
            @ApiParam(value = "ER lookup result none found, Generate will fail") @RequestParam(value = "failIfNoneFound", required = false, defaultValue = "false") final Boolean failIfNoneFound,
            @ApiParam(value = RemremGenerateServiceConstants.CONNECT_TO_EXTERNAL_ERS) @RequestParam(value = "connectToExternalERs", required = false, defaultValue = "true")  final Boolean connectToExternalERs,
            @ApiParam(value = RemremGenerateServiceConstants.LIMIT) @RequestParam(value = "limit", required = false, defaultValue = "1") final int limit,
            @ApiParam(value = "JSON message", required = true) @RequestBody JsonObject bodyJson) {

        try {
            bodyJson = erLookup(bodyJson, failIfMultipleFound, failIfNoneFound, connectToExternalERs, limit);
            MsgService msgService = getMessageService(msgProtocol);
            String response;
            if (msgService != null) {
                response = msgService.generateMsg(msgType, bodyJson);
                JsonElement parsedResponse = parser.parse(response);
                if (!parsedResponse.getAsJsonObject().has(RemremGenerateServiceConstants.JSON_ERROR_MESSAGE_FIELD)) {
                    return new ResponseEntity<>(parsedResponse, HttpStatus.OK);
                } else {
                    return new ResponseEntity<>(parsedResponse, HttpStatus.BAD_REQUEST);
                }
            } else {
                return new ResponseEntity<>(parser.parse(RemremGenerateServiceConstants.NO_SERVICE_ERROR),
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
        } catch (REMGenerateException e1) {
            if (e1.getMessage().contains(Integer.toString(HttpStatus.NOT_ACCEPTABLE.value()))) {
                return new ResponseEntity<>(parser.parse(e1.getMessage()), HttpStatus.NOT_ACCEPTABLE);
            } else {
                return new ResponseEntity<>(parser.parse(e1.getMessage()), HttpStatus.EXPECTATION_FAILED);
            }
        } catch (Exception e) {
            log.error("Unexpected exception caught", e);
            return new ResponseEntity<>(parser.parse(RemremGenerateServiceConstants.INTERNAL_SERVER_ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonObject erLookup(final JsonObject bodyJson, Boolean failIfMultipleFound, Boolean failIfNoneFound,
    		final Boolean connectToExternalERs, final int limit)
            throws REMGenerateException {

        // Checking ER lookup enabled or not
        if (erlookupConfig.getEventRepositoryEnabled() && bodyJson.toString().contains("%lookup%")) {
            JsonArray lookupLinks = bodyJson.get("eventParams").getAsJsonObject().get("links").getAsJsonArray();
            JsonArray links = new JsonArray();
            for (int i = 0; i < lookupLinks.size(); i++) {
                if (lookupLinks.get(i).toString().contains("%lookup%")) {
                    String[] ids = null;

                    // prepare ER Query
                    String Query = ERLookupController.getQueryfromLookup(lookupLinks.get(i).getAsJsonObject());
                    String url = erlookupConfig.getErURL() + Query + String.format("&shallow=%s&pageSize=%d",connectToExternalERs, limit);

                    // Execute ER Query
                    int j = 0;
                    while (j < 2) {
                        try {
                            response = restTemplate.getForEntity(url, String.class);
                            if (response.getStatusCode() == HttpStatus.OK) {
                                log.info("The result from Event Repository is: " + response.getStatusCodeValue());
                                break;
                            }
                        } catch (Exception e) {
                            if (++j >= 2)
                                log.error("unable to connect configured Event Repository URL" + e.getMessage());
                        }
                    }
                    String responseBody = response.getBody();
                    ids = ERLookupController.getIdsfromResponseBody(responseBody);

                    // Checking ER lookup result
                    if (failIfMultipleFound && ids != null && ids.length > 1) {
                        throw new REMGenerateException(
                                RemremGenerateServiceConstants.UNAVAILABLE_FOR_FAILIFMULTIPLEFOUND);
                    } else if (failIfNoneFound && ids.length == 0) {
                        throw new REMGenerateException(RemremGenerateServiceConstants.UNAVAILABLE_FOR_FAILIFNONEFOUND);
                    }

                    // Replace lookup values
                    ERLookupController.convertbodyJsontoLookupJson(ids, lookupLinks.get(i).getAsJsonObject(), links);
                } else {
                    links.add(lookupLinks.get(i).getAsJsonObject());
                }
                bodyJson.get("eventParams").getAsJsonObject().add("links", links);
            }
        } else {
            return bodyJson;
        }
        return bodyJson;
    }

    /**
     * Used to display the versions of generate and all loaded protocols.
     *
     * @return json with version details.
     */
    @ApiOperation(value = "To get versions of generate and all loaded protocols", response = String.class)
    @RequestMapping(value = "/versions", method = RequestMethod.GET)
    public JsonElement getVersions() {
        Map<String, Map<String, String>> versions = new VersionService().getMessagingVersions();
        return parser.parse(versions.toString());
    }

    /**
     * Returns available Eiffel event types as listed in EiffelEventType enum.
     *
     * @return string collection with event types.
     */
    @ApiOperation(value = "To get available eiffel event types based on the message protocol", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Event  types got successfully"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Message protocol is invalid") })
    @RequestMapping(value = "/event_types/{mp}", method = RequestMethod.GET)
    public ResponseEntity<?> getEventTypes(
            @ApiParam(value = "message protocol", required = true) @PathVariable("mp") final String msgProtocol,
            @ApiIgnore final RequestEntity requestEntity) {
        MsgService msgService = getMessageService(msgProtocol);
        try {
            if (msgService != null) {
                return presentResponse(msgService.getSupportedEventTypes(), HttpStatus.OK, requestEntity);
            } else {
                return presentResponse(parser.parse(RemremGenerateServiceConstants.NO_SERVICE_ERROR),
                        HttpStatus.SERVICE_UNAVAILABLE, requestEntity);
            }
        } catch (Exception e) {
            log.error("Unexpected exception caught", e);
            return presentResponse(parser.parse(RemremGenerateServiceConstants.INTERNAL_SERVER_ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR, requestEntity);
        }
    }

    /**
     * Returns an eiffel event template matching the type specified in the path.
     *
     * @return json containing eiffel event template.
     */
    @ApiOperation(value = "To get eiffel event template of specified event type", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Template got successfully"),
            @ApiResponse(code = 400, message = "Requested template is not available"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 503, message = "Message protocol is invalid") })
    @RequestMapping(value = "/template/{type}/{mp}", method = RequestMethod.GET)
    public ResponseEntity<?> getEventTypeTemplate(
            @ApiParam(value = "message type", required = true) @PathVariable("type") final String msgType,
            @ApiParam(value = "message protocol", required = true) @PathVariable("mp") final String msgProtocol,
            @ApiIgnore final RequestEntity requestEntity) {
        MsgService msgService = getMessageService(msgProtocol);
        try {
            if (msgService != null) {
                JsonElement template = msgService.getEventTemplate(msgType);
                if (template != null) {
                    return presentResponse(template, HttpStatus.OK, requestEntity);
                } else {
                    return presentResponse(parser.parse(RemremGenerateServiceConstants.NO_TEMPLATE_ERROR),
                            HttpStatus.NOT_FOUND, requestEntity);
                }
            } else {
                return presentResponse(parser.parse(RemremGenerateServiceConstants.NO_SERVICE_ERROR),
                        HttpStatus.SERVICE_UNAVAILABLE, requestEntity);
            }
        } catch (Exception e) {
            log.error("Unexpected exception caught", e);
            return presentResponse(parser.parse(RemremGenerateServiceConstants.INTERNAL_SERVER_ERROR),
                    HttpStatus.INTERNAL_SERVER_ERROR, requestEntity);
        }
    }

    private MsgService getMessageService(final String messageProtocol) {
        for (MsgService service : msgServices) {
            if (service.getServiceName().equals(messageProtocol)) {
                return service;
            }
        }
        return null;
    }

    /**
     * To display pretty formatted json in browser
     * 
     * @param rawJson
     *            json content
     * @return html formatted json string
     */
    private String buildHtmlReturnString(final String rawJson) {
        final String htmlHead = "<!DOCTYPE html><html><body><pre>";
        final String htmlTail = "</pre></body></html>";
        return htmlHead + rawJson + htmlTail;
    }

    /**
     * To display response in browser or application
     * 
     * @param message
     *            json content
     * @param status
     *            response code for the HTTP request
     * @param requestEntity
     *            entity of the HTTP request
     * @return entity to present response in browser or application
     */
    private ResponseEntity<?> presentResponse(final Object message, final HttpStatus status,
            final RequestEntity requestEntity) {
        if (requestEntity.getHeaders().getAccept().contains(MediaType.TEXT_HTML)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return new ResponseEntity<>(buildHtmlReturnString(gson.toJson(message)), status);
        } else {
            return new ResponseEntity<>(message, status);
        }
    }
}