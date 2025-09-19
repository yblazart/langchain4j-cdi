/** */
package dev.langchain4j.cdi.faulttolerance.spi;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Buhake Sindi
 * @since 11 August 2025
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@InterceptorBinding
@interface ApplyFaultTolerance {

    static class ApplyFaultToleranceLiteral extends AnnotationLiteral<ApplyFaultTolerance>
            implements ApplyFaultTolerance {}
}
