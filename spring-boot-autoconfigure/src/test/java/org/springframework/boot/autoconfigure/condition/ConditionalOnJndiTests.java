/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.condition;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ConditionalOnJndi}
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 */
public class ConditionalOnJndiTests {

	private String initialContextFactory;

	private ConfigurableApplicationContext context;

	private MockableOnJndi condition = new MockableOnJndi();

	@After
	public void close() {
		TestableInitialContextFactory.clearAll();
		if (this.initialContextFactory != null) {
			System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
					this.initialContextFactory);
		}
		else {
			System.clearProperty(Context.INITIAL_CONTEXT_FACTORY);
		}
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void jndiNotAvailable() {
		load(JndiAvailableConfiguration.class);
		assertPresent(false);
	}

	@Test
	public void jndiAvailable() {
		setupJndi();
		load(JndiAvailableConfiguration.class);
		assertPresent(true);
	}

	@Test
	public void jndiLocationNotBound() {
		setupJndi();
		load(JndiConditionConfiguration.class);
		assertPresent(false);
	}

	@Test
	public void jndiLocationBound() {
		setupJndi();
		TestableInitialContextFactory.bind("java:/FooManager", new Object());
		load(JndiConditionConfiguration.class);
		assertPresent(true);
	}

	@Test
	public void jndiLocationNotFound() {
		ConditionOutcome outcome = this.condition.getMatchOutcome(null,
				mockMetaData("java:/a"));
		assertThat(outcome.isMatch(), equalTo(false));
	}

	@Test
	public void jndiLocationFound() {
		this.condition.setFoundLocation("java:/b");
		ConditionOutcome outcome = this.condition.getMatchOutcome(null,
				mockMetaData("java:/a", "java:/b"));
		assertThat(outcome.isMatch(), equalTo(true));
	}

	private void setupJndi() {
		this.initialContextFactory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
		System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
				TestableInitialContextFactory.class.getName());
	}

	private void assertPresent(boolean expected) {
		int expectedNumber = expected ? 1 : 0;
		Matcher<Iterable<String>> matcher = iterableWithSize(expectedNumber);
		assertThat(this.context.getBeansOfType(String.class).values(), is(matcher));
	}

	private void load(Class<?> config, String... environment) {
		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
		EnvironmentTestUtils.addEnvironment(applicationContext, environment);
		applicationContext.register(config);
		applicationContext.register(JndiConditionConfiguration.class);
		applicationContext.refresh();
		this.context = applicationContext;
	}

	private AnnotatedTypeMetadata mockMetaData(String... value) {
		AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
		Map<String, Object> attributes = new HashMap<String, Object>();
		attributes.put("value", value);
		given(metadata.getAnnotationAttributes(ConditionalOnJndi.class.getName()))
				.willReturn(attributes);
		return metadata;
	}

	@Configuration
	@ConditionalOnJndi
	static class JndiAvailableConfiguration {

		@Bean
		public String foo() {
			return "foo";
		}
	}

	@Configuration
	@ConditionalOnJndi("java:/FooManager")
	static class JndiConditionConfiguration {

		@Bean
		public String foo() {
			return "foo";
		}
	}

	private static class MockableOnJndi extends OnJndiCondition {

		private boolean jndiAvailable = true;

		private String foundLocation;

		@Override
		protected boolean isJndiAvailable() {
			return this.jndiAvailable;
		}

		@Override
		protected JndiLocator getJndiLocator(String[] locations) {
			return new JndiLocator(locations) {
				@Override
				public String lookupFirstLocation() {
					return MockableOnJndi.this.foundLocation;
				}
			};
		}

		public void setFoundLocation(String foundLocation) {
			this.foundLocation = foundLocation;
		}
	}

	public static class TestableInitialContextFactory implements InitialContextFactory {

		private static TestableContext context;

		@Override
		public Context getInitialContext(Hashtable<?, ?> environment)
				throws NamingException {
			return getContext();
		}

		public static void bind(String name, Object obj) {
			try {
				getContext().bind(name, obj);
			}
			catch (NamingException ex) {
				throw new IllegalStateException(ex);
			}
		}

		public static void clearAll() {
			getContext().clearAll();
		}

		private static TestableContext getContext() {
			if (context == null) {
				try {
					context = new TestableContext();
				}
				catch (NamingException ex) {
					throw new IllegalStateException(ex);
				}
			}
			return context;
		}

		private static class TestableContext extends InitialContext {

			private final Map<String, Object> bindings = new HashMap<String, Object>();

			private TestableContext() throws NamingException {
				super(true);
			}

			@Override
			public void bind(String name, Object obj) throws NamingException {
				this.bindings.put(name, obj);
			}

			@Override
			public Object lookup(String name) throws NamingException {
				return this.bindings.get(name);
			}

			@Override
			public Hashtable<?, ?> getEnvironment() throws NamingException {
				return new Hashtable<Object, Object>(); // Used to detect if JNDI is
														// available
			}

			public void clearAll() {
				this.bindings.clear();
			}
		}
	}

}
