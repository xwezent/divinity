package com.divinity.deobf.controlflow;

import com.divinity.cfg.BasicBlock;
import java.util.*;

public final class StateTransitionGraph {

    private final Map<Integer, Transition> transitions;

    public StateTransitionGraph() {
        this.transitions = new LinkedHashMap<>();
    }

    public void addTransition(int fromState, int toState, BasicBlock block) {
        transitions.put(fromState, new Transition(toState, block));
    }

    public Transition getTransition(int state) {
        return transitions.get(state);
    }

    public Map<Integer, Transition> transitions() {
        return transitions;
    }

    public Set<Integer> getAllStates() {
        Set<Integer> states = new LinkedHashSet<>();
        states.addAll(transitions.keySet());
        for (Transition t : transitions.values()) {
            if (t.nextState() != null && t.nextState() != -1) {
                states.add(t.nextState());
            }
        }
        return states;
    }

    public List<Integer> getExecutionPath(int startState) {
        List<Integer> path = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        int current = startState;

        while (current != -1 && visited.add(current)) {
            path.add(current);
            Transition trans = transitions.get(current);
            if (trans == null || trans.nextState() == null) break;
            current = trans.nextState();
        }

        return path;
    }

    public boolean isTerminalState(int state) {
        Transition trans = transitions.get(state);
        return trans == null || trans.nextState() == null || trans.nextState() == -1;
    }

    public record Transition(Integer nextState, BasicBlock block) {}
}
