/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot.autoconfig;

import com.thoughtworks.xstream.XStream;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.Repository;
import org.axonframework.spring.eventsourcing.SpringPrototypeAggregateFactory;
import org.axonframework.springboot.autoconfig.context.Animal;
import org.axonframework.springboot.autoconfig.context.Cat;
import org.axonframework.springboot.autoconfig.context.CreateCatCommand;
import org.axonframework.springboot.autoconfig.context.CreateDogCommand;
import org.axonframework.springboot.autoconfig.context.Dog;
import org.axonframework.springboot.autoconfig.context.RenameAnimalCommand;
import org.axonframework.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.common.StringUtils.lowerCaseFirstCharacterOf;

/**
 * Test class validating Aggregate Polymorphism works as intended when using Spring Boot autoconfiguration.
 *
 * @author Steven van Beelen
 */
class AggregatePolymorphismAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(DefaultContext.class)
                                                               .withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    void polymorphicAggregateWiringHandlesCommandsAndIsEventSourcedAsExpected() {
        String catId = UUID.randomUUID().toString();
        String dogId = UUID.randomUUID().toString();

        testApplicationContext.withUserConfiguration(PolymorphicAggregateContext.class)
                              .run(context -> {
                                  CommandGateway commandGateway =
                                          context.getBean("commandGateway", CommandGateway.class);

                                  commandGateway.sendAndWait(new CreateCatCommand(catId, "Felix"));
                                  commandGateway.sendAndWait(new RenameAnimalCommand(catId, "Wokkel"));
                                  commandGateway.sendAndWait(new CreateDogCommand(dogId, "Milou"));
                                  commandGateway.sendAndWait(new RenameAnimalCommand(dogId, "Medor"));
                              });
    }

    @Test
    void polymorphicAggregateWiringConstructsSingleAggregateFactory() {
        String catFactoryBeanName = aggregateFactoryBeanNameFor(Cat.class);
        String dogFactoryBeanName = aggregateFactoryBeanNameFor(Dog.class);
        String animalFactoryBeanName = aggregateFactoryBeanNameFor(Animal.class);

        testApplicationContext.withUserConfiguration(PolymorphicAggregateContext.class)
                              .run(context -> {
                                  // Only a single Aggregate Factory should exist, for both Cats and Dogs.
                                  assertThat(context).hasSingleBean(SpringPrototypeAggregateFactory.class);
                                  assertThat(context)
                                          .getBean(catFactoryBeanName, SpringPrototypeAggregateFactory.class)
                                          .isNull();
                                  assertThat(context)
                                          .getBean(dogFactoryBeanName, SpringPrototypeAggregateFactory.class)
                                          .isNull();
                                  assertThat(context)
                                          .getBean(animalFactoryBeanName, SpringPrototypeAggregateFactory.class)
                                          .isNotNull();
                              });
    }

    private static String aggregateFactoryBeanNameFor(Class<?> aggregateClass) {
        return lowerCaseFirstCharacterOf(aggregateClass.getSimpleName()) + "AggregateFactory";
    }

    @Test
    void polymorphicAggregateWiringConstructsSingleRepository() {
        String animalRepositoryBeanName = repositoryBeanName(Animal.class);

        testApplicationContext.withUserConfiguration(PolymorphicAggregateContext.class)
                              .run(context -> {
                                  assertThat(context).hasSingleBean(Repository.class);
                                  assertThat(context).getBean(Repository.class)
                                                     .isInstanceOf(EventSourcingRepository.class);
                                  String[] namesForRepositoryBeans = context.getBeanNamesForType(Repository.class);
                                  assertThat(namesForRepositoryBeans.length).isEqualTo(1);

                                  assertThat(namesForRepositoryBeans[0]).isEqualTo(animalRepositoryBeanName);
                              });
    }

    private static String repositoryBeanName(@SuppressWarnings("SameParameterValue") Class<?> aggregateClass) {
        return lowerCaseFirstCharacterOf(aggregateClass.getSimpleName()) + "Repository";
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    static class DefaultContext {

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public XStream xStream() {
            return TestSerializer.xStreamSerializer().getXStream();
        }
    }

    @Configuration
    @ComponentScan(basePackages = {"org.axonframework.springboot.autoconfig.context"})
    static class PolymorphicAggregateContext {

    }
}
