/**
 *
 */
package dev.langchain4j.cdi.faulttolerance.spi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * @author Buhake Sindi
 * @since 18 August 2025
 */
class FaultToleranceInterceptorHandler {

    private final BeanManager beanManager;
    private final InterceptionType interceptionType;
    private final List<Interceptor<?>> interceptors;
    private int position = 0;

    /**
     * @param beanManager
     * @param interceptionType
     * @param interceptors
     */
    public FaultToleranceInterceptorHandler(BeanManager beanManager, InterceptionType interceptionType,
            List<Interceptor<?>> interceptors) {
        super();
        this.beanManager = beanManager;
        this.interceptionType = interceptionType;
        this.interceptors = interceptors;
    }

    Object handle(final InvocationContext invocationContext) throws Exception {
        if (position < interceptors.size()) {
            @SuppressWarnings("unchecked")
            Interceptor<Object> interceptor = (Interceptor<Object>) interceptors.get(position++);
            CreationalContext<?> context = beanManager.createCreationalContext(interceptor);
            Object interceptorInstance = beanManager.getReference(interceptor, interceptor.getBeanClass(), context);
            return interceptor.intercept(interceptionType, interceptorInstance, new InvocationContextChain(invocationContext));
        }

        return invocationContext.proceed();
    }

    private final class InvocationContextChain implements InvocationContext {

        private final InvocationContext delegate;

        /**
         * @param delegate
         */
        public InvocationContextChain(InvocationContext delegate) {
            super();
            this.delegate = delegate;
        }

        @Override
        public Object getTarget() {
            // TODO Auto-generated method stub
            return delegate.getTarget();
        }

        @Override
        public Object getTimer() {
            // TODO Auto-generated method stub
            return delegate.getTimer();
        }

        @Override
        public Method getMethod() {
            // TODO Auto-generated method stub
            return delegate.getMethod();
        }

        @Override
        public Constructor<?> getConstructor() {
            // TODO Auto-generated method stub
            return delegate.getConstructor();
        }

        @Override
        public Object[] getParameters() {
            // TODO Auto-generated method stub
            return delegate.getParameters();
        }

        @Override
        public void setParameters(Object[] params) {
            // TODO Auto-generated method stub
            delegate.setParameters(params);
        }

        @Override
        public Map<String, Object> getContextData() {
            // TODO Auto-generated method stub
            return delegate.getContextData();
        }

        @Override
        public Object proceed() throws Exception {
            // TODO Auto-generated method stub
            return FaultToleranceInterceptorHandler.this.handle(delegate);
        }
    }
}
