package com.example.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.*;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

@Service
public final class MethodHighlighter implements EditorFactoryListener {

    private final Map<String, MethodMetadata> allowedMethods = new HashMap<>();
    private final Map<String, String> libraryToCSV;
    private final Set<String> ignoredWarnings = new HashSet<>(); // Track ignored warnings

    public MethodHighlighter() {
        libraryToCSV = Map.of(
                "numpy", "DABCS-functions/numpy-dabcs.csv",
                "pandas", "DABCS-functions/pandas-dabcs.csv",
                "sklearn", "DABCS-functions/sklearn-dabcs.csv"
        );
    }

    public File extractResourceToTempFile(String resourcePath) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }

        File tempFile = File.createTempFile("dabcs", ".csv");
        tempFile.deleteOnExit();

        try (OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }


    private Map<String, MethodMetadata> extractMethodNamesFromCSV(String filePath) throws IOException {
        Map<String, MethodMetadata> methodMap = new HashMap<>();
        File file = extractResourceToTempFile(filePath);

        if (!file.exists()) {
            System.err.println("CSV file not found: " + filePath);
            return methodMap;
        }

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] headers = reader.readNext();
            if (headers == null) return methodMap;

            int fqnIndex = -1, versionIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase();
                if (h.equals("fqn")) fqnIndex = i;
                else if (h.equals("version")) versionIndex = i;
            }

            if (fqnIndex == -1 || versionIndex == -1) return methodMap;

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length <= Math.max(fqnIndex, versionIndex)) continue;

                String fqn = row[fqnIndex].trim();
                String version = row[versionIndex].trim();
                if (fqn.isEmpty() || version.isEmpty()) continue;

                String methodName = null;

                // Match method: def XYZ(...) or method: __init__(...)
                if (fqn.contains("method:")) {
                    int methodIndex = fqn.indexOf("method:");
                    int start = methodIndex + "method:".length();
                    int end = fqn.indexOf("(", start);
                    if (start >= 0 && end > start) {
                        methodName = fqn.substring(start, end).trim();
                    }
                }

                // Extract param from fqn using "param:"
                String param = null;
                if (fqn.contains("param:")) {
                    int start = fqn.indexOf("param:") + "param:".length();
                    int end = fqn.indexOf(":", start);
                    if (end == -1) end = fqn.length();
                    param = fqn.substring(start, end).trim();
                }

                // Determine key to use
                String key = null;
                if ("__init__".equals(methodName)) {
                    // Use class name instead
                    if (fqn.contains("class:")) {
                        int classStart = fqn.indexOf("class:") + "class:".length();
                        int classEnd = fqn.indexOf("(", classStart);
                        if (classEnd == -1) classEnd = fqn.indexOf(":", classStart);
                        if (classEnd == -1) classEnd = fqn.length();
                        String className = fqn.substring(classStart, classEnd).trim();
                        if (!className.isEmpty()) key = className;
                    }
                } else {
                    key = methodName;
                }

                // Store param in map under the correct key
                if (key != null && param != null && !version.isEmpty()) {
                    if (!methodMap.containsKey(key)) {
                        methodMap.put(key, new MethodMetadata(param, version));
                    } else {
                        methodMap.get(key).addParam(param);
                    }
                }
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }

        return methodMap;
    }






    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor != null) {
            attachDocumentListener(editor);
            try {
                detectLibrariesAndLoadMethods(editor.getDocument());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            highlightMethodCalls(editor);
        }
    }

    private void attachDocumentListener(Editor editor) {
        Document document = editor.getDocument();
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                try {
                    detectLibrariesAndLoadMethods(document);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                highlightMethodCalls(editor);
            }
        });
    }

    private void detectLibrariesAndLoadMethods(Document document) throws IOException {
        Set<String> detectedLibraries = detectImportedLibraries(document);
        allowedMethods.clear(); // Clear previously loaded methods

        for (String library : detectedLibraries) {
            String csvPath = libraryToCSV.get(library);
            if (csvPath != null) {
                allowedMethods.putAll(extractMethodNamesFromCSV(csvPath));
            }
        }
    }

    private Set<String> detectImportedLibraries(Document document) {
        Set<String> detectedLibraries = new HashSet<>();
        String[] lines = document.getText().split("\n");

        for (String line : lines) {
            if (line.startsWith("import ") || line.startsWith("from ")) {
                if (line.contains("numpy")) {
                    detectedLibraries.add("numpy");
                } else if (line.contains("pandas")) {
                    detectedLibraries.add("pandas");
                } else if (line.contains("sklearn")) {
                    detectedLibraries.add("sklearn");
                }
            }
        }

        return detectedLibraries;
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        // Clean up resources if necessary
    }

    private void highlightMethodCalls(Editor editor) {
        Document document = editor.getDocument();
        MarkupModel markupModel = editor.getMarkupModel();
        markupModel.removeAllHighlighters();

        String text = document.getText();
        Pattern pattern = Pattern.compile("\\b(?:\\w+\\.)?(\\w+)\\s*\\("); // matches methodName(
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String methodName = matcher.group(1);

            if (!allowedMethods.containsKey(methodName)) continue;
            if (ignoredWarnings.contains(methodName)) continue;

            MethodMetadata metadata = allowedMethods.get(methodName);
            int startParen = matcher.end();
            int endParen = findClosingParen(text, startParen - 1);
            if (endParen == -1) continue;

            String argsContent = text.substring(startParen, endParen);

            // Skip highlighting ONLY if ALL params are explicitly defined
            boolean allParamsPresent = true;
            for (String param : metadata.getParams()) {
                if (!(argsContent.contains(param + " =") || argsContent.contains(param + "="))) {
                    allParamsPresent = false;
                    break;
                }
            }

            if (allParamsPresent) continue;

            // Highlight
            TextAttributes textAttributes = new TextAttributes();
            textAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
            textAttributes.setEffectColor(Color.RED);

            RangeHighlighter highlighter = markupModel.addRangeHighlighter(
                    matcher.start(1),
                    matcher.end(1),
                    HighlighterLayer.SELECTION,
                    textAttributes,
                    HighlighterTargetArea.EXACT_RANGE
            );

            highlighter.setGutterIconRenderer(
                    new MethodGutterIconRenderer(methodName, editor, highlighter, this, metadata)
            );
        }
    }




    public void ignoreWarning(String methodName) {
        ignoredWarnings.add(methodName);
        System.out.println("Added method to ignored warnings: " + methodName);
    }

    private int findClosingParen(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

}
