package edu.uw.easysrl.qasrl;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;

/**
 * Parsing results returned by the base CCG parser. Resolved dependencies and categories are extracted from syntaxTree.
 * The score comes from the n-best parser.
 * Created by luheng on 1/12/16.
 */
public class Parse implements Serializable {
    public SyntaxTreeNode syntaxTree;
    public List<Category> categories;
    public Set<ResolvedDependency> dependencies;
    public double score;

    private final ImmutableList<String> words;
    private final ImmutableList<Category> categoriesImmutable;

    public ImmutableList<String> getWords() {
        return words;
    }

    public ImmutableList<Category> getCategories() {
        return categoriesImmutable;
    }

    public Parse(SyntaxTreeNode syntaxTree, List<Category> categories, Set<ResolvedDependency> dependencies,
                 double score) {
        this.syntaxTree = syntaxTree;
        this.categories = categories;
        this.dependencies = dependencies;
        this.score = score;

        words = syntaxTree.getLeaves().stream()
            .map(SyntaxTreeNodeLeaf::getWord)
            .collect(toImmutableList());
        categoriesImmutable = ImmutableList.copyOf(categories);
    }

    public Parse(List<String> words, List<Category> categories) {
        this.syntaxTree = null;
        this.categories = categories;
        this.dependencies = null;
        this.score  =1.0;
        this.words = ImmutableList.copyOf(words);
        categoriesImmutable = ImmutableList.copyOf(categories);
    }

    public Parse(SyntaxTreeNode syntaxTree, List<Category> categories, Set<ResolvedDependency> dependencies) {
        this(syntaxTree, categories, dependencies, 1.0);
    }
}
