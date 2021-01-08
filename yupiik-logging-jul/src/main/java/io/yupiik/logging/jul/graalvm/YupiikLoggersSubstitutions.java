package io.yupiik.logging.jul.graalvm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import io.yupiik.logging.jul.YupiikLoggers;

@TargetClass(YupiikLoggers.class)
final class YupiikLoggersSubstitutions {
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance)
    private YupiikLoggers.State state;
}
