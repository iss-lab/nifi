/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeasy.rules.support.JsonRuleDefinitionReader;
import org.jeasy.rules.support.RuleDefinition;
import org.jeasy.rules.support.RuleDefinitionReader;
import org.jeasy.rules.support.YamlRuleDefinitionReader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory which transform file based rules into NiFi Rules API.  Rules formats supported are NiFi Rules format and
 * Easy Rules MVEL and SPEL formats. JSON and YaML file types are support for both formats
 */
public class RulesFactory {

    enum FileType {
        YAML, JSON;
    }

    enum FileFormat {
        NIFI, MVEL, SPEL;
    }

    public static List<Rule> createRules(String ruleFile, String ruleFileType, String rulesFileFormat) throws Exception{
        FileFormat fileFormat = FileFormat.valueOf(rulesFileFormat);
        switch (fileFormat){
            case NIFI:
                return createRulesFromNiFiFormat(ruleFile, ruleFileType);
            case MVEL:
            case SPEL:
                return createRulesFromEasyRulesFormat(ruleFile, ruleFileType, rulesFileFormat);
            default:
                return null;
        }
    }

    private static List<Rule> createRulesFromEasyRulesFormat(String ruleFile, String ruleFileType, String ruleFileFormat) throws Exception{

        RuleDefinitionReader reader = FileType.valueOf(ruleFileType).equals(FileType.YAML)
                                      ? new YamlRuleDefinitionReader() : new JsonRuleDefinitionReader();

        List<RuleDefinition> ruleDefinitions = reader.read(new FileReader(ruleFile));

        return ruleDefinitions.stream().map(ruleDefinition -> {

            Rule rule = new Rule();
            rule.setName(ruleDefinition.getName());
            rule.setDescription(ruleDefinition.getDescription());
            rule.setPriority(ruleDefinition.getPriority());
            rule.setCondition(ruleDefinition.getCondition());
            List<Action> actions = ruleDefinition.getActions().stream().map(ruleAction -> {
                Action action = new Action();
                action.setType("EXPRESSION");
                Map<String,String> attributes = new HashMap<>();
                attributes.put("command", ruleAction);
                attributes.put("type", ruleFileFormat);
                action.setAttributes(attributes);
                return action;
            }).collect(Collectors.toList());
            rule.setActions(actions);
            return rule;

        }).collect(Collectors.toList());
    }

    private static List<Rule> createRulesFromNiFiFormat(String ruleFile, String ruleFileType) throws Exception{
        FileType type = FileType.valueOf(ruleFileType.toUpperCase());
        if (type.equals(FileType.YAML)) {
            return yamlToRules(ruleFile);
        } else if (type.equals(FileType.JSON)) {
            return jsonToRules(ruleFile);
        } else {
            return null;
        }
    }

    private static List<Rule> yamlToRules(String rulesFile) throws FileNotFoundException {
        List<Rule> rules = new ArrayList<>();
        Yaml yaml = new Yaml(new Constructor(Rule.class));
        File yamlFile = new File(rulesFile);
        InputStream inputStream = new FileInputStream(yamlFile);
        for (Object object : yaml.loadAll(inputStream)) {
            if (object instanceof Rule) {
                rules.add((Rule) object);
            }
        }
        return rules;
    }

    private static List<Rule> jsonToRules(String rulesFile) throws Exception {
        List<Rule> rules;
        InputStreamReader isr = new InputStreamReader(new FileInputStream(rulesFile));
        final ObjectMapper objectMapper = new ObjectMapper();
        rules = objectMapper.readValue(isr, new TypeReference<List<Rule>>(){});
        return rules;
    }
}