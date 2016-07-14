/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.aws.autoconfigure.cache;

import com.amazonaws.services.elasticache.AmazonElastiCache;
import com.amazonaws.services.elasticache.model.CacheCluster;
import com.amazonaws.services.elasticache.model.DescribeCacheClustersRequest;
import com.amazonaws.services.elasticache.model.DescribeCacheClustersResult;
import com.amazonaws.services.elasticache.model.Endpoint;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cloud.aws.autoconfigure.context.MetaData;
import org.springframework.cloud.aws.autoconfigure.context.MetaData.Context;
import org.springframework.cloud.aws.autoconfigure.context.MetaDataServer;
import org.springframework.cloud.aws.core.env.stack.ListableStackResourceFactory;
import org.springframework.cloud.aws.core.env.stack.StackResource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@MetaData(@Context(path = "/latest/meta-data/instance-id", value = "testInstanceId"))
public class ElastiCacheAutoConfigurationTest {
	@ClassRule
	public static MetaDataServer metaDataServer = new MetaDataServer();

	private AnnotationConfigApplicationContext context;

	@After
	public void tearDown() throws Exception {
		this.context.close();
	}

	@Test
	public void cacheManager_configuredMultipleCachesWithStack_configuresCacheManager() throws Exception {
		//Arrange
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(MockCacheConfigurationWithStackCaches.class);
		this.context.register(ElastiCacheAutoConfiguration.class);

		//Act
		this.context.refresh();

		//Assert
		CacheManager cacheManager = this.context.getBean(CachingConfigurer.class).cacheManager();
		assertTrue(cacheManager.getCacheNames().contains("sampleCacheOneLogical"));
		assertTrue(cacheManager.getCacheNames().contains("sampleCacheTwoLogical"));
		assertEquals(2, cacheManager.getCacheNames().size());
	}

	@Test
	public void cacheManager_configuredNoCachesWithNoStack_configuresNoCacheManager() throws Exception {
		//Arrange
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(ElastiCacheAutoConfiguration.class);

		//Act
		this.context.refresh();

		//Assert
		CacheManager cacheManager = this.context.getBean(CachingConfigurer.class).cacheManager();
		assertEquals(0, cacheManager.getCacheNames().size());
	}

	@AfterClass
	public static void shutdownCacheServer() throws Exception {
		TestMemcacheServer.stopServer();
	}

	@Configuration
	public static class MockCacheConfigurationWithStackCaches {

		@Bean
		public AmazonElastiCache amazonElastiCache() {
			AmazonElastiCache amazonElastiCache = Mockito.mock(AmazonElastiCache.class);
			int port = TestMemcacheServer.startServer();
			DescribeCacheClustersRequest sampleCacheOneLogical = new DescribeCacheClustersRequest().withCacheClusterId("sampleCacheOneLogical");
			sampleCacheOneLogical.setShowCacheNodeInfo(true);

			Mockito.when(amazonElastiCache.describeCacheClusters(sampleCacheOneLogical)).
					thenReturn(new DescribeCacheClustersResult().withCacheClusters(new CacheCluster().
							withConfigurationEndpoint(new Endpoint().withAddress("localhost").withPort(port)).
							withEngine("memcached")));

			DescribeCacheClustersRequest sampleCacheTwoLogical = new DescribeCacheClustersRequest().withCacheClusterId("sampleCacheTwoLogical");
			sampleCacheTwoLogical.setShowCacheNodeInfo(true);

			Mockito.when(amazonElastiCache.describeCacheClusters(sampleCacheTwoLogical)).
					thenReturn(new DescribeCacheClustersResult().withCacheClusters(new CacheCluster().
							withConfigurationEndpoint(new Endpoint().withAddress("localhost").withPort(port)).
							withEngine("memcached")));
			return amazonElastiCache;
		}

		@Bean
		public ListableStackResourceFactory stackResourceFactory() {
			ListableStackResourceFactory resourceFactory = Mockito.mock(ListableStackResourceFactory.class);
			Mockito.when(resourceFactory.resourcesByType("AWS::ElastiCache::CacheCluster")).thenReturn(Arrays.asList(
					new StackResource("sampleCacheOneLogical", "sampleCacheOne", "AWS::ElastiCache::CacheCluster"),
					new StackResource("sampleCacheTwoLogical", "sampleCacheTwo", "AWS::ElastiCache::CacheCluster")));
			return resourceFactory;
		}
	}
}
