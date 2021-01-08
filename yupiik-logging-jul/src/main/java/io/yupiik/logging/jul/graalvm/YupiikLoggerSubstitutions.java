package io.yupiik.logging.jul.graalvm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import io.yupiik.logging.jul.logger.YupiikLogger;

import java.util.logging.Logger;

@TargetClass(YupiikLogger.class)
final class YupiikLoggerSubstitutions {
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
    private volatile Logger delegate;
}
