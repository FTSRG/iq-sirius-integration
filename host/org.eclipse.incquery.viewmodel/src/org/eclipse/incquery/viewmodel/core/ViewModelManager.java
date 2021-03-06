package org.eclipse.incquery.viewmodel.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.incquery.patternlanguage.emf.specification.SpecificationBuilder;
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternModel;
import org.eclipse.incquery.runtime.api.IQuerySpecification;
import org.eclipse.incquery.runtime.api.IncQueryEngine;
import org.eclipse.incquery.runtime.emf.EMFScope;
import org.eclipse.incquery.runtime.evm.api.ActivationLifeCycle;
import org.eclipse.incquery.runtime.evm.specific.ConflictResolvers;
import org.eclipse.incquery.runtime.evm.specific.Lifecycles;
import org.eclipse.incquery.runtime.evm.specific.TransactionalSchedulers;
import org.eclipse.incquery.runtime.evm.specific.event.IncQueryActivationStateEnum;
import org.eclipse.incquery.runtime.evm.specific.resolver.FixedPriorityConflictResolver;
import org.eclipse.incquery.runtime.evm.specific.scheduler.UpdateCompleteBasedScheduler.UpdateCompleteBasedSchedulerFactory;
import org.eclipse.incquery.runtime.evm.update.IQEngineUpdateCompleteProvider;
import org.eclipse.incquery.runtime.exception.IncQueryException;
import org.eclipse.incquery.viewmodel.configuration.AttributeRuleDescriptor;
import org.eclipse.incquery.viewmodel.configuration.Configuration;
import org.eclipse.incquery.viewmodel.configuration.ElementRuleDescriptor;
import org.eclipse.incquery.viewmodel.configuration.ReferenceRuleDescriptor;
import org.eclipse.incquery.viewmodel.configuration.RuleDescriptor;
import org.eclipse.incquery.viewmodel.core.rules.AttributeRule;
import org.eclipse.incquery.viewmodel.core.rules.ElementRule;
import org.eclipse.incquery.viewmodel.core.rules.ReferenceRule;
import org.eclipse.incquery.viewmodel.core.rules.ViewModelRule;
import org.eclipse.incquery.viewmodel.traceability.TraceabilityModelManager;
import org.eclipse.viatra.emf.runtime.rules.eventdriven.EventDrivenTransformationRule;
import org.eclipse.viatra.emf.runtime.rules.eventdriven.EventDrivenTransformationRuleFactory;
import org.eclipse.viatra.emf.runtime.transformation.eventdriven.EventDrivenTransformation;
import org.eclipse.viatra.emf.runtime.transformation.eventdriven.EventDrivenTransformation.EventDrivenTransformationBuilder;
import org.eclipse.viatra.emf.runtime.transformation.eventdriven.ExecutionSchemaBuilder;
import org.eclipse.viatra.emf.runtime.transformation.eventdriven.InconsistentEventSemanticsException;

/**
 * Manager class which do the necessary steps for the transformation: initializes the transformation
 * 	engine and the traceability model manager, and build the transformation rules based on the given
 * 	configuration model.
 * 
 * If the ConfigurationModel is serialized, the {@link ViewModelManager#ViewModelManager(URI)} constructor
 * 	can be used to instantiate the manager. In this case the {@link Configuration#getSourceModelURI()}, and
 * 	{@link Configuration#getTargetModelURI()} methods have to return a valid URI instance pointing at the
 * 	corresponding source and target models.
 * 
 * If the ConfigurationModel is not serialized (or it has been loaded previously), the {@link ViewModelManager#ViewModelManager(Configuration)}
 * 	constructor can be used to initialize the manager. In this case, if the {@link Configuration#getSourceModel()} or
 * 	{@link Configuration#getTargetModel()} method returns null, the corresponding URI instance will be used to
 * 	initialize the source and target models if it's possible, otherwise an Exception will be thrown.
 * 
 * @author lengyela
 *
 */
public class ViewModelManager {
	
	// The root of the configuration model
	private Configuration configurationModel;

	// Manager for the traceability model
	private TraceabilityModelManager traceabilityModelManager;
	
	// Descriptor for the transformations
	private EventDrivenTransformation transformation;
	
	// Transactional editing domain for the source model (if it exists)
	private TransactionalEditingDomain sourceTransactionalEditingDomain;
	
	// Transactional editing domain for the target model (if it exists)
	private TransactionalEditingDomain targetTransactionalEditingDomain;
	
	// Loaded IQuerySpecification instances
	private Map<String, IQuerySpecification<?>> querySpecifications;

	
	
	/**
	 * If the ConfigurationModel is serialized, the {@link ViewModelManager#ViewModelManager(URI)} constructor
	 * 	can be used to instantiate the manager. In this case the {@link Configuration#getSourceModelURI()}, and
	 * 	{@link Configuration#getTargetModelURI()} methods have to return a valid URI instance pointing at the
	 * 	corresponding source and target models.
	 * 
	 * TODO
	 * Not implemented yet.
	 * @param configurationModelURI
	 */
	public ViewModelManager(URI configurationModelURI) {
		// TODO implement...
		throw new UnsupportedOperationException();
	}
	
	/**
	 * If the ConfigurationModel is not serialized (or it has been loaded previously), the {@link ViewModelManager#ViewModelManager(Configuration)}
	 * 	constructor can be used to initialize the manager. In this case, if the {@link Configuration#getSourceModel()} or
	 * 	{@link Configuration#getTargetModel()} method returns null, the corresponding URI instance will be used to
	 * 	initialize the source and target models if it's possible, otherwise an Exception will be thrown.
	 * 
	 * TODO
	 * 	Jelen verzióban, ha a source-on van TED, akkor az EVM-et úgy kell belőni, hogy azt használja,
	 *  egyébként pedig a sima ütemezőt (Update-re). Ha a target-hez van TED, akkor az egyes action-ök abban futnak
	 *  (később a CommandStack-et javítani kell a visszavonások miatt), egyébként pedig sima getter/setter hívásokat használok a target modellen.
	 */
	public ViewModelManager(Configuration configurationModel) throws IncQueryException {
		if (configurationModel == null) {
			throw new IllegalArgumentException("The 'configurationModel' parameter can not be null!");
		}
		
		this.configurationModel = configurationModel;
		
		this.traceabilityModelManager = new TraceabilityModelManager();

		this.querySpecifications = new HashMap<String, IQuerySpecification<?>>();
		
		
		ResourceSet resourceSet = new ResourceSetImpl();
		
		// If the sourceModel attribute is null, we try to load the source model based on the sourceModelURI attribute
		if (this.configurationModel.getSourceModel() == null) {
			if (this.configurationModel.getSourceModelURI() == null) {
				throw new IllegalArgumentException("If the sourceModel attribute is null, the sourceModelURI attribute must not be null!");
			}
			
			this.configurationModel.setSourceModel(resourceSet.getResource(
					URI.createPlatformResourceURI(this.configurationModel.getSourceModelURI(), true), false));
			
			if (this.configurationModel.getSourceModel() == null) {
				throw new IllegalArgumentException("The given sourceModelURI is not a correct PlatformResourceURI!");
			}
		}

		// If the targetModel attribute is null, we try to load the target model based on the targetModelURI attribute
		if (this.configurationModel.getTargetModel() == null) {
			if (this.configurationModel.getTargetModelURI() == null) {
				throw new IllegalArgumentException("If the sourceModel attribute is null, the sourceModelURI attribute must not be null!");
			}

			this.configurationModel.setTargetModel(resourceSet.getResource(
					URI.createPlatformResourceURI(this.configurationModel.getTargetModelURI(), true), false));
			
			if (this.configurationModel.getTargetModel() == null) {
				throw new IllegalArgumentException("The given sourceModelURI is not a correct PlatformResourceURI!");
			}
		}
		
		// Load pattern resources
		if (this.configurationModel.getPatternResourceURIs() != null) {
			ResourceSet eiqResourceSet = new ResourceSetImpl();
			
			URI patternResourceURI = null;
			Resource patternResource =  null;
			for (String uri : this.configurationModel.getPatternResourceURIs()) {
				patternResourceURI = URI.createPlatformResourceURI(uri, true);
				
				if (patternResourceURI != null
						&& this.configurationModel.getPatternResources() != null
						&& !containsResourceWithURI(this.configurationModel.getPatternResources(), patternResourceURI)) {
					patternResource = eiqResourceSet.getResource(patternResourceURI, true);
					
					this.configurationModel.getPatternResources().add(patternResource);
				}
			}
		}
		
		// Create IQuerySpecification instances from pattern resources
		PatternModel patternModel = null;
		IQuerySpecification<?> querySpecification = null;
		SpecificationBuilder specificationBuilder = new SpecificationBuilder();
		for (Resource resource : this.configurationModel.getPatternResources()) {
			if (!resource.getContents().isEmpty() && (resource.getContents().get(0) instanceof PatternModel)) {
				patternModel = (PatternModel) resource.getContents().get(0);

				for (Pattern pattern : patternModel.getPatterns()) {
					querySpecification = specificationBuilder.getOrCreateSpecification(pattern);
					
					querySpecifications.put(querySpecification.getFullyQualifiedName(), querySpecification);
				}
			}
		}
		
		
		this.sourceTransactionalEditingDomain = TransactionUtil.getEditingDomain(
				configurationModel.getSourceModel());
		
		this.targetTransactionalEditingDomain = TransactionUtil.getEditingDomain(
				configurationModel.getTargetModel());
	}
	
	/**
	 * Initialize the manager
	 * @throws InconsistentEventSemanticsException
	 * @throws IncQueryException
	 */
	public void initialize() throws InconsistentEventSemanticsException, IncQueryException {
		IncQueryEngine smEngine = IncQueryEngine.on(new EMFScope(configurationModel.getSourceModel()));
		
		FixedPriorityConflictResolver conflictResolver = ConflictResolvers.createFixedPriorityResolver();

		EventDrivenTransformationBuilder edtb = EventDrivenTransformation.forEngine(smEngine);

		ExecutionSchemaBuilder esb = new ExecutionSchemaBuilder();
		esb.setEngine(smEngine);
		esb.setConflictResolver(conflictResolver);

		/*
		 * If there isn't an existing TransactionalEditingDomain for the sourceModel, we set a UpdateCompleteBasedScheduler,
		 * 	otherwise we use a TransactionalScheduler
		 */
		if (sourceTransactionalEditingDomain == null) {
			esb.setScheduler(new UpdateCompleteBasedSchedulerFactory(new IQEngineUpdateCompleteProvider(smEngine)));
		} else {
			esb.setScheduler(TransactionalSchedulers.getTransactionSchedulerFactory(sourceTransactionalEditingDomain));
		}
		
		
		edtb.setSchema(esb.build());
		
		EventDrivenTransformationRule builtRule = null;
		ViewModelRule<? extends RuleDescriptor> rule = null;
		
		for (ElementRuleDescriptor ruleDescriptor : configurationModel.getElementRuleDescriptors()) {
			rule = new ElementRule(ruleDescriptor, this);
			
			builtRule = buildRule(rule);
			
			edtb.addRule(builtRule);
			
			conflictResolver.setPriority(builtRule.getRuleSpecification(), rule.getPriority());
		}
		
		for (ReferenceRuleDescriptor ruleDescriptor : configurationModel.getReferenceRuleDescriptors()) {
			rule = new ReferenceRule(ruleDescriptor, this);
			
			builtRule = buildRule(rule);
			
			edtb.addRule(builtRule);
			
			conflictResolver.setPriority(builtRule.getRuleSpecification(), rule.getPriority());
		}
		
		for (AttributeRuleDescriptor ruleDescriptor : configurationModel.getAttributeRuleDescriptors()) {
			rule = new AttributeRule(ruleDescriptor, this);
			
			builtRule = buildRule(rule);
			
			edtb.addRule(builtRule);
			
			conflictResolver.setPriority(builtRule.getRuleSpecification(), rule.getPriority());
		}
		
		transformation = edtb.build();
		
		transformation.getExecutionSchema().startUnscheduledExecution();
	}
	
	/**
	 * Disposer method for the manager.
	 */
	public void dispose() {
		if (transformation != null) {
			transformation.getExecutionSchema().dispose();
		}
	}
	
	/**
	 * 
	 * @return The configuration model instance
	 */
	public Configuration getConfigurationModel() {
		return this.configurationModel;
	}
	
	/**
	 * 
	 * @param patternFQN The fully qualified name of the pattern
	 * @return An IQuerySpecification instance with the given FQN or NULL if it doesn't exist
	 */
	public IQuerySpecification<?> getQuerySpecification(String patternFQN) {
		if (querySpecifications != null) {
			return querySpecifications.get(patternFQN);
		}
		
		return null;
	}
	
	/**
	 * 
	 * @return The {@link TransactionalEditingDomain} for the target model if it's exist,
	 * 	otherwise null
	 */
	public TransactionalEditingDomain getTargetTransactionalEditingDomain() {
		return this.targetTransactionalEditingDomain;
	}
	
	/**
	 * 
	 * @return The {@link TraceabilityModelManager} instance if it's exist,
	 * 	otherwise null
	 */
	public TraceabilityModelManager getTraceabilityModelManager() {
		return this.traceabilityModelManager;
	}
	
	/**
	 * Builds the transformation rules based on the given configuration model.
	 * @param rule The {@link ViewModelRule} instance which will be built
	 * @return {@link EventDrivenTransformationRule} instance
	 * @throws InconsistentEventSemanticsException
	 */
	private EventDrivenTransformationRule buildRule(ViewModelRule<? extends RuleDescriptor> rule)
			throws InconsistentEventSemanticsException {
		EventDrivenTransformationRuleFactory.EventDrivenTransformationBuilder builder
				= new EventDrivenTransformationRuleFactory().createRule();

		builder.precondition(rule.getQuerySpecification());
		
		ActivationLifeCycle alc = rule.getActivationLifeCycle();
		if (Lifecycles.getDefault(false, false).equals(alc)) {
			builder.action(IncQueryActivationStateEnum.APPEARED, rule.getAppearedAction());
		} else if (Lifecycles.getDefault(true, false).equals(alc)) {
			builder.action(IncQueryActivationStateEnum.APPEARED, rule.getAppearedAction());
			builder.action(IncQueryActivationStateEnum.UPDATED, rule.getUpdatedAction());
		} else if (Lifecycles.getDefault(false, true).equals(alc)) {
			builder.action(IncQueryActivationStateEnum.APPEARED, rule.getAppearedAction());
			builder.action(IncQueryActivationStateEnum.DISAPPEARED, rule.getDisappearedAction());
		} else if (Lifecycles.getDefault(true, true).equals(alc)) {
			builder.action(IncQueryActivationStateEnum.APPEARED, rule.getAppearedAction());
			builder.action(IncQueryActivationStateEnum.DISAPPEARED, rule.getDisappearedAction());
			builder.action(IncQueryActivationStateEnum.UPDATED, rule.getUpdatedAction());
		}

		builder.addLifeCycle(alc);
		
		return builder.build();
	}
	
	private boolean containsResourceWithURI(Collection<Resource> resources, URI uri) {
		if (resources == null || uri == null) {
			throw new IllegalArgumentException("Parameters must not be null!");
		}
		
		for (Resource resource : resources) {
			if (resource.getURI() != null && resource.getURI().equals(uri)) {
				return true;
			}
		}
		
		return false;
	}
}
