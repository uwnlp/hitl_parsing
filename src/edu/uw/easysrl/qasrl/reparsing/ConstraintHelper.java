package edu.uw.easysrl.qasrl.reparsing;

import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.model.Constraint;
import edu.uw.easysrl.syntax.model.Constraint.*;

/**
 * Created by luheng on 9/1/16.
 */
public class ConstraintHelper {
    public static boolean isSatisfiedBy(Constraint constraint, Parse parse) {
        if (SupertagConstraint.class.isInstance(constraint)) {
            SupertagConstraint c = (SupertagConstraint) constraint;
            return parse.categories.get(c.getPredId()) == c.getCategory();
        }
        if (AttachmentConstraint.class.isInstance(constraint)) {
            AttachmentConstraint c = (AttachmentConstraint) constraint;
            return parse.dependencies.stream()
                    .anyMatch(dep -> (dep.getHead() == c.getHeadId() && dep.getArgument() == c.getArgId())
                            || (dep.getHead() == c.getArgId() && dep.getArgument() == c.getHeadId()));
        }
        if (DisjunctiveAttachmentConstraint.class.isInstance(constraint)) {
            DisjunctiveAttachmentConstraint c = (DisjunctiveAttachmentConstraint) constraint;
            return parse.dependencies.stream()
                    .anyMatch(dep -> (dep.getHead() == c.getHeadId() && c.getArgIds().contains(dep.getArgument()))
                            || (c.getArgIds().contains(dep.getHead()) && dep.getArgument() == c.getHeadId()));
        }
        return false;
    }

}
