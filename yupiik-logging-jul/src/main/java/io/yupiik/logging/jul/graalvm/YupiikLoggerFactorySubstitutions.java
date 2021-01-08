package io.yupiik.logging.jul.graalvm;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import io.yupiik.logging.jul.YupiikLoggerFactory;
import io.yupiik.logging.jul.YupiikLoggers;

@TargetClass(YupiikLoggerFactory.class)
final class YupiikLoggerFactorySubstitutions {
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
    private static volatile YupiikLoggers delegate;
}
