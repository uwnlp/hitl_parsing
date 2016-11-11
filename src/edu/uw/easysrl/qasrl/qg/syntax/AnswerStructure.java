package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains information about the answer spans.
 * Created by luheng on 3/19/16.
 */
public class AnswerStructure {
    public final ImmutableList<Integer> argumentIndices;
    public final ImmutableSet<ResolvedDependency> adjunctDependencies;
    // For non-checkbox version, this an answer structure is non-singled headed. This changes the logic of containedIn.
    public final boolean singleHeaded;
    public final boolean headIsVP;

    public AnswerStructure(Collection<Integer> argIds, boolean singleHeaded) {
        this.argumentIndices = ImmutableList.copyOf(argIds.stream().sorted().collect(Collectors.toList()));
        this.adjunctDependencies = ImmutableSet.of();
        this.singleHeaded = singleHeaded;
        this.headIsVP = false;
    }

    public AnswerStructure(Collection<Integer> argIds, final ImmutableSet<ResolvedDependency> adjunctDependencies,
                           boolean headIsVP) {
        this.argumentIndices = ImmutableList.copyOf(argIds.stream().sorted().collect(Collectors.toList()));
        this.adjunctDependencies = adjunctDependencies;
        this.singleHeaded = true;
        this.headIsVP = headIsVP;
    }

    // FIXME
    public boolean equals(final Object other) {
        return AnswerStructure.class.isInstance(other) &&
                argumentIndices.equals(((AnswerStructure) other).argumentIndices) &&
                adjunctDependencies.equals(((AnswerStructure) other).adjunctDependencies);
    }

    /**
     * Return all the dependencies that's relevant to this structure.
     * @param dependencies
     * @return
     */
    public ImmutableSet<ResolvedDependency> filter(Collection<ResolvedDependency> dependencies) {
        if (!singleHeaded) {
            return dependencies.stream()
                    .collect(Collectors.groupingBy(ResolvedDependency::getHead))
                    .values().stream()
                    .filter(deps -> {
                        ImmutableList<Integer> argList = deps.stream()
                                .map(ResolvedDependency::getArgument)
                                .distinct()
                                .collect(GuavaCollectors.toImmutableList());
                        return argList.size() == argumentIndices.size() &&
                                argList.containsAll(argumentIndices) && argumentIndices.containsAll(argList);
                    })
                    .flatMap(Collection::stream)
                    .collect(GuavaCollectors.toImmutableSet());
        }
        if (!headIsVP) {
            return dependencies.stream()
                    .filter(d -> d.getArgument() == argumentIndices.get(0))
                    .collect(GuavaCollectors.toImmutableSet());
        }
        return dependencies.stream()
                .filter(d -> d.getHead() == argumentIndices.get(0))
                .collect(GuavaCollectors.toImmutableSet());
    }

    public String toString(final ImmutableList<String> words) {
        String argIdsStr = argumentIndices.stream().map(String::valueOf).collect(Collectors.joining(","));
        String argHeadsStr = argumentIndices.stream().map(words::get).collect(Collectors.joining(","));
        String adjDepsStr = adjunctDependencies.stream()
                        .map(dep -> String.format("%s->%s", words.get(dep.getHead()),words.get(dep.getArgument())))
                        .collect(Collectors.joining(";"));
        return argIdsStr + ":" + argHeadsStr + "," + adjDepsStr;
    }
}
