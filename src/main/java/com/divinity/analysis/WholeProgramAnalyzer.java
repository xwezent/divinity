package com.divinity.analysis;

import com.divinity.cfg.ControlFlowGraph;
import com.divinity.classfile.ClassFileParser;
import java.util.*;
import java.util.concurrent.*;

public final class WholeProgramAnalyzer {

    private final Map<String, ClassFileParser> allClasses;
    private final Map<String, ControlFlowGraph> allCfgs;
    private final CallGraph callGraph;
    private final Map<String, Set<String>> dependencies;

    public WholeProgramAnalyzer() {
        this.allClasses = new ConcurrentHashMap<>();
        this.allCfgs = new ConcurrentHashMap<>();
        this.callGraph = new CallGraph();
        this.dependencies = new ConcurrentHashMap<>();
    }

    public void addClass(String className, ClassFileParser parser, ControlFlowGraph cfg) {
        allClasses.put(className, parser);
        allCfgs.put(className, cfg);
    }

    public WholeProgramAnalysisResult analyze() {
        buildCallGraph();
        analyzeDataFlow();
        detectUnusedCode();
        findEntryPoints();
        computeMetrics();

        return new WholeProgramAnalysisResult(
            callGraph,
            dependencies,
            computeStatistics()
        );
    }

    private void buildCallGraph() {
        for (Map.Entry<String, ControlFlowGraph> entry : allCfgs.entrySet()) {
            String caller = entry.getKey();
            ControlFlowGraph cfg = entry.getValue();

            Set<String> callees = extractCallees(cfg);
            for (String callee : callees) {
                callGraph.addEdge(caller, callee);
            }
        }
    }

    private Set<String> extractCallees(ControlFlowGraph cfg) {
        Set<String> callees = new LinkedHashSet<>();
        return callees;
    }

    private void analyzeDataFlow() {
        for (String className : allClasses.keySet()) {
            Set<String> deps = new LinkedHashSet<>();

            Set<String> directCalls = callGraph.getCallees(className);
            deps.addAll(directCalls);

            for (String call : directCalls) {
                deps.addAll(callGraph.getCallees(call));
            }

            dependencies.put(className, deps);
        }
    }

    private void detectUnusedCode() {
        Set<String> reachable = new LinkedHashSet<>();
        Set<String> entryPoints = findEntryPoints();

        Queue<String> queue = new ArrayDeque<>(entryPoints);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (reachable.add(current)) {
                queue.addAll(callGraph.getCallees(current));
            }
        }

        Set<String> unused = new LinkedHashSet<>(allClasses.keySet());
        unused.removeAll(reachable);
    }

    private Set<String> findEntryPoints() {
        Set<String> entryPoints = new LinkedHashSet<>();

        for (String className : allClasses.keySet()) {
            if (className.contains("Main") || className.contains("Application")) {
                entryPoints.add(className);
            }
        }

        return entryPoints;
    }

    private void computeMetrics() {
    }

    private Map<String, Object> computeStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalClasses", allClasses.size());
        stats.put("totalMethods", callGraph.size());
        stats.put("averageComplexity", 0.0);
        return stats;
    }

    public record WholeProgramAnalysisResult(CallGraph callGraph,
                                             Map<String, Set<String>> dependencies,
                                             Map<String, Object> statistics) {}

    public static class CallGraph {
        private final Map<String, Set<String>> edges = new ConcurrentHashMap<>();

        public void addEdge(String from, String to) {
            edges.computeIfAbsent(from, k -> ConcurrentHashMap.newKeySet()).add(to);
        }

        public Set<String> getCallees(String method) {
            return edges.getOrDefault(method, Set.of());
        }

        public Set<String> getCallers(String method) {
            Set<String> callers = new LinkedHashSet<>();
            for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
                if (entry.getValue().contains(method)) {
                    callers.add(entry.getKey());
                }
            }
            return callers;
        }

        public int size() {
            return edges.size();
        }

        public boolean isReachable(String from, String to) {
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>();
            queue.add(from);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(to)) return true;
                if (visited.add(current)) {
                    queue.addAll(getCallees(current));
                }
            }

            return false;
        }
    }
}
