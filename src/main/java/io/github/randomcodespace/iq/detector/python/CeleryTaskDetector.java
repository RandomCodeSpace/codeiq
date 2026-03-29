package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.AbstractRegexDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CeleryTaskDetector extends AbstractRegexDetector {

    // @app.task or @shared_task or @celery.task with optional name param
    private static final Pattern TASK_DECORATOR = Pattern.compile(
            "@(?:\\w+\\.)?(?:task|shared_task)\\(?"
            + "(?:.*?name\\s*=\\s*['\"]([^'\"]+)['\"])?"
            + "[^)]*\\)?\\s*\\n\\s*def\\s+(\\w+)",
            Pattern.DOTALL
    );

    // task.delay(...) or task.apply_async(...)
    private static final Pattern TASK_CALL = Pattern.compile(
            "(\\w+)\\.(delay|apply_async|s|si|signature)\\("
    );

    @Override
    public String getName() {
        return "python.celery_tasks";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        String text = ctx.content();
        if (text == null || text.isEmpty()) {
            return DetectorResult.empty();
        }
        String filePath = ctx.filePath();
        String moduleName = ctx.moduleName();

        // Detect task definitions
        Matcher taskMatcher = TASK_DECORATOR.matcher(text);
        while (taskMatcher.find()) {
            String taskName = taskMatcher.group(1) != null ? taskMatcher.group(1) : taskMatcher.group(2);
            String funcName = taskMatcher.group(2);
            int line = findLineNumber(text, taskMatcher.start());

            String queueId = "queue:" + (moduleName != null ? moduleName : "") + ":celery:" + taskName;
            CodeNode queueNode = new CodeNode();
            queueNode.setId(queueId);
            queueNode.setKind(NodeKind.QUEUE);
            queueNode.setLabel("celery:" + taskName);
            queueNode.setModule(moduleName);
            queueNode.setFilePath(filePath);
            queueNode.setLineStart(line);
            queueNode.getProperties().put("broker", "celery");
            queueNode.getProperties().put("task_name", taskName);
            queueNode.getProperties().put("function", funcName);
            nodes.add(queueNode);

            String methodId = "method:" + filePath + "::" + funcName;
            CodeNode methodNode = new CodeNode();
            methodNode.setId(methodId);
            methodNode.setKind(NodeKind.METHOD);
            methodNode.setLabel(funcName);
            methodNode.setFqn(filePath + "::" + funcName);
            methodNode.setModule(moduleName);
            methodNode.setFilePath(filePath);
            methodNode.setLineStart(line);
            nodes.add(methodNode);

            CodeEdge consumesEdge = new CodeEdge();
            consumesEdge.setId(methodId + "->consumes->" + queueId);
            consumesEdge.setKind(EdgeKind.CONSUMES);
            consumesEdge.setSourceId(methodId);
            edges.add(consumesEdge);
        }

        // Detect task invocations
        Matcher callMatcher = TASK_CALL.matcher(text);
        while (callMatcher.find()) {
            String taskRef = callMatcher.group(1);
            String callType = callMatcher.group(2);
            int line = findLineNumber(text, callMatcher.start());

            String queueId = "queue:" + (moduleName != null ? moduleName : "") + ":celery:" + taskRef;
            String callerId = "method:" + filePath + "::caller_l" + line;

            CodeEdge producesEdge = new CodeEdge();
            producesEdge.setId(callerId + "->produces->" + queueId);
            producesEdge.setKind(EdgeKind.PRODUCES);
            producesEdge.setSourceId(callerId);
            edges.add(producesEdge);
        }

        return DetectorResult.of(nodes, edges);
    }
}
