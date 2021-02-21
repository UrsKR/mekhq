/*
 * Copyright (c) 2019-2021 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.preferences;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import megamek.common.annotations.Nullable;
import mekhq.MekHQ;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The root class for MekHQ user nameToPreferencesMap system.
 */
public class MekHqPreferences {
    //region Variable Declarations
    private static final String PREFERENCES_TOKEN = "preferences";
    private static final String CLASS_TOKEN = "class";
    private static final String ELEMENTS_TOKEN = "elements";
    private static final String NAME_TOKEN = "element";
    private static final String VALUE_TOKEN = "value";
    private final Map<String, PreferencesNode> nameToPreferencesMap;
    //endregion Variable Declarations

    //region Constructors
    public MekHqPreferences() {
        nameToPreferencesMap = new HashMap<>();
    }
    //endregion Constructors

    //region Getters/Setters
    public Map<String, PreferencesNode> getNameToPreferencesMap() {
        return nameToPreferencesMap;
    }
    //endregion Getters/Setters

    public PreferencesNode forClass(final Class classToManage) {
        PreferencesNode preferences = getNameToPreferencesMap().getOrDefault(classToManage.getName(), null);
        if (preferences == null) {
            preferences = new PreferencesNode(classToManage);
            getNameToPreferencesMap().put(classToManage.getName(), preferences);
        }
        return preferences;
    }

    //region File I/O
    //region Write To File
    public void saveToFile(final String filePath) {
        try {
            try (FileOutputStream output = new FileOutputStream(filePath)) {
                MekHQ.getLogger().debug("Saving MekHQ nameToPreferencesMap to: " + filePath);

                final JsonFactory factory = new JsonFactory();
                final JsonGenerator writer = factory.createGenerator(output).useDefaultPrettyPrinter();
                writer.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);

                writer.writeStartObject();
                writer.writeFieldName(PREFERENCES_TOKEN);
                writer.writeStartArray();

                // Write each PreferencesNode
                for (final Map.Entry<String, PreferencesNode> preferences : getNameToPreferencesMap().entrySet()) {
                    writePreferencesNode(writer, preferences);
                }

                writer.writeEndArray();
                writer.writeEndObject();

                writer.close();
            }
        } catch (FileNotFoundException e) {
            MekHQ.getLogger().error("Could not save nameToPreferencesMap to: " + filePath, e);
        } catch (IOException e) {
            MekHQ.getLogger().error("Error writing to the nameToPreferencesMap file: " + filePath, e);
        }
    }

    private static void writePreferencesNode(final JsonGenerator writer, final Map.Entry<String, PreferencesNode> nodeInfo) throws IOException {
        writer.writeStartObject();
        writer.writeStringField(CLASS_TOKEN, nodeInfo.getKey());
        writer.writeFieldName(ELEMENTS_TOKEN);
        writer.writeStartArray();

        // Write all PreferenceElement in this node
        for (final Map.Entry<String, String> element : nodeInfo.getValue().getFinalValues().entrySet()) {
            writePreferenceElement(writer, element);
        }

        writer.writeEndArray();
        writer.writeEndObject();
    }

    private static void writePreferenceElement(final JsonGenerator writer, final Map.Entry<String, String> element) throws IOException {
        writer.writeStartObject();
        writer.writeStringField(NAME_TOKEN, element.getKey());
        writer.writeStringField(VALUE_TOKEN, element.getValue());
        writer.writeEndObject();
    }
    //endregion Write To File

    //region Load From File
    public void loadFromFile(final String filePath) {
        try {
            try (FileInputStream input = new FileInputStream(filePath)) {
                MekHQ.getLogger().info("Loading MekHQ user preferences from: " + filePath);

                final JsonFactory factory = new JsonFactory();
                final JsonParser parser = factory.createParser(input);

                assert parser.nextToken() == JsonToken.START_OBJECT
                        : "Expected an object start ({)" + getParserInformation(parser);
                assert (parser.nextToken() == JsonToken.FIELD_NAME) || parser.getCurrentName().equals(PREFERENCES_TOKEN)
                        : "Expected a field called (" + PREFERENCES_TOKEN + ")" + getParserInformation(parser);
                assert parser.nextToken() == JsonToken.START_ARRAY
                        : "Expected an array start ([)" + getParserInformation(parser);

                // Parse all PreferencesNode
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    try {
                        readPreferencesNode(parser, getNameToPreferencesMap());
                    } catch (IOException e) {
                        MekHQ.getLogger().error("Error reading node. " + getParserInformation(parser), e);
                    }
                }

                parser.close();

                MekHQ.getLogger().info("Finished loading user preferences");
            }
        } catch (FileNotFoundException e) {
            MekHQ.getLogger().error("No MekHQ user preferences file found: " + filePath, e);
        } catch (IOException e) {
            MekHQ.getLogger().error("Error reading from the user preferences file: " + filePath, e);
        }
    }

    private static String getParserInformation(final @Nullable JsonParser parser) throws IOException {
        if (parser == null) {
            return "";
        }

        return ". Current token: " + parser.getCurrentName() +
                ". Line number: " + parser.getCurrentLocation().getLineNr() +
                ". Column number: " + parser.getCurrentLocation().getColumnNr();
    }

    private static void readPreferencesNode(final JsonParser parser, final Map<String, PreferencesNode> nodes) throws IOException {
        if ((parser.currentToken() != JsonToken.START_OBJECT)
                || ((parser.nextToken() != JsonToken.FIELD_NAME) && !parser.getCurrentName().equals(CLASS_TOKEN))) {
            return;
        }

        final String className = parser.nextTextValue();

        if (((parser.nextToken() != JsonToken.FIELD_NAME) && !parser.getCurrentName().equals(ELEMENTS_TOKEN))
                || (parser.nextToken() != JsonToken.START_ARRAY)) {
            return;
        }

        final HashMap<String, String> elements = new HashMap<>();

        // Parse all PreferenceElement in this node
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            try {
                readPreferenceElement(parser, elements);
            } catch (IOException e) {
                MekHQ.getLogger().warning("Error reading elements for node: " + className + ".", e);
            }
        }

        try {
            final PreferencesNode node = new PreferencesNode(Class.forName(className));
            node.initialize(elements);
            nodes.put(node.getNode().getName(), node);
        } catch (ClassNotFoundException e) {
            MekHQ.getLogger().error("No class with name " + className + " found", e);
        }
    }

    private static void readPreferenceElement(final JsonParser parser, final Map<String, String> elements) throws IOException {
        if ((parser.currentToken() != JsonToken.START_OBJECT)
                || ((parser.nextToken() != JsonToken.FIELD_NAME) && !parser.getCurrentName().equals(NAME_TOKEN))) {
            return;
        }

        final String name = parser.nextTextValue();

        if ((parser.nextToken() != JsonToken.FIELD_NAME) && !parser.getCurrentName().equals(VALUE_TOKEN)) {
            return;
        }

        final String value = parser.nextTextValue();

        if (parser.nextToken() != JsonToken.END_OBJECT) {
            return;
        }

        elements.put(name, value);
    }
    //endregion Load From File
}
