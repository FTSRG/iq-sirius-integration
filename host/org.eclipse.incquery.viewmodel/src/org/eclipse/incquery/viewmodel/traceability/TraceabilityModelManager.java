package org.eclipse.incquery.viewmodel.traceability;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.incquery.viewmodel.configuration.TransformationRuleDescriptor;
import org.eclipse.incquery.viewmodel.traceability.patterns.GetTraceByAttributeTargetMatch;
import org.eclipse.incquery.viewmodel.traceability.patterns.GetTraceByEObjectTargetMatch;
import org.eclipse.incquery.viewmodel.traceability.patterns.GetTraceByReferenceTargetMatch;
import org.eclipse.incquery.viewmodel.traceability.patterns.GetTraceSourceMatch;
import org.eclipse.incquery.viewmodel.traceability.patterns.util.GetTraceByAttributeTargetQuerySpecification;
import org.eclipse.incquery.viewmodel.traceability.patterns.util.GetTraceByEObjectTargetQuerySpecification;
import org.eclipse.incquery.viewmodel.traceability.patterns.util.GetTraceByReferenceTargetQuerySpecification;
import org.eclipse.incquery.viewmodel.traceability.patterns.util.GetTraceSourceQuerySpecification;
import org.eclipse.incquery.viewmodel.traceability.util.TraceQuerySpecification;
import org.eclipse.viatra.query.runtime.api.GenericPatternMatch;
import org.eclipse.viatra.query.runtime.api.ViatraQueryEngine;
import org.eclipse.viatra.query.runtime.emf.EMFScope;
import org.eclipse.viatra.query.runtime.exception.ViatraQueryException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * This class manages the traceability model.
 * 
 * @author lengyela
 *
 */
public class TraceabilityModelManager {
	
	private ViatraQueryEngine engine;

	private Traceability traceability;
	
	private Map<Integer, TraceQuerySpecification> traceQuerySpecifications;
	
	
	
	public TraceabilityModelManager() throws ViatraQueryException {
		this.traceability = createTraceabilityEObject();
		
		this.engine = ViatraQueryEngine.on(new EMFScope(traceability));
		
		this.traceQuerySpecifications = Maps.newHashMap();
	}
	
	/**
	 * This method adds the given sources to the given trace. If the hidden parameter is set to true,
	 * 	they will be added as hidden.
	 * @param trace The trace, which the sources will be added to
	 * @param sources The sources, which will be added to the trace
	 * @param hidden Determine, if the added sources are hidden or not
	 */
	public void addSources(Trace trace, Map<String, Object> sources, boolean hidden) {
		if (trace == null || sources == null) {
			throw new IllegalArgumentException("The trace and sources parameters can not be null!");
		}
		
		GetTraceSourceMatch match = null;
		GetTraceSourceMatch emptyMatch = null;
		GetTraceSourceQuerySpecification querySpecification = null;
		try {
			querySpecification = GetTraceSourceQuerySpecification.instance();
			
			for (Entry<String, Object> entry : sources.entrySet()) {
				emptyMatch = querySpecification.newEmptyMatch();
				emptyMatch.setTrace(trace);
				emptyMatch.setHidden(hidden);
				emptyMatch.setPatternParameterName(entry.getKey());
				emptyMatch.setSource(entry.getValue());
				
				match = querySpecification.getMatcher(engine).getOneArbitraryMatch(emptyMatch);
				if (match != null && match.getTraceSource() != null) {
					// If the source exists in the traceability model, increment the referenced counter by one
					match.getTraceSource().setReferenced(match.getTraceSource().getReferenced()+1);
				} else {
					// If the source doesn't exist in the traceability model, create a new instance
					if (entry.getValue() instanceof EObject) {
						// If the parameter is an EObject
						
						trace.getSources().add(
								createEObjectSource(1L, hidden, entry.getKey(), (EObject) entry.getValue()));
					} else {
						// If the parameter is an Object
						
						trace.getSources().add(
								createJavaObjectSource(1L, hidden, entry.getKey(), entry.getValue()));
					}
				}			
			}
		} catch (ViatraQueryException e) {
			e.printStackTrace();
			// TODO
		}
	}
	
	public EObjectSource createEObjectSource(long referenced, boolean hidden, String patternParameterName, EObject source) {
		EObjectSource result = createEObjectSourceEObject();
		result.setReferenced(referenced);
		result.setHidden(hidden);
		result.setPatternParameterName(patternParameterName);
		result.setSource(source);
		
		return result;
	}
	
	public JavaObjectSource createJavaObjectSource(long referenced, boolean hidden, String patternParameterName, Object source) {
		JavaObjectSource result = createJavaObjectSourceEObject();
		result.setReferenced(referenced);
		result.setHidden(hidden);
		result.setPatternParameterName(patternParameterName);
		result.setSource(source);
		
		return result;
	}
	
	public Trace createTrace(long ruleDescriptorId, Map<String, Object> sources, EObject targetEObject) {
		Trace result = createTraceEObject();

		result.setRuleDescriptorId(ruleDescriptorId);
		
		addSources(result, sources, false);
		
		EObjectTarget eObjectTarget = createEObjectTargetEObject();
		eObjectTarget.setTarget(targetEObject);
			
		result.setTarget(eObjectTarget);

		traceability.getTraces().add(result);

		return result;
	}	
	
	public Trace createTrace(long ruleDescriptorId, Map<String, Object> sources, EObjectTarget owner, EObjectTarget target, String referenceName) {
		Trace result = createTraceEObject();
		
		result.setRuleDescriptorId(ruleDescriptorId);
		
		addSources(result, sources, false);
		
		ReferenceTarget referenceTarget = createReferenceTargetEObject();
		referenceTarget.setOwner(owner);
		referenceTarget.setTarget(target);
		referenceTarget.setReferenceName(referenceName);
		
		result.setTarget(referenceTarget);
		
		traceability.getTraces().add(result);
		
		return result;
	}
	
	public Trace createTrace(long ruleDescriptorId, Map<String, Object> sources, EObjectTarget owner, Object target, String attributeName) {
		Trace result = createTraceEObject();
		
		result.setRuleDescriptorId(ruleDescriptorId);
		
		addSources(result, sources, false);
		
		AttributeTarget attributeTarget = createAttributeTargetEObject();
		attributeTarget.setOwner(owner);
		attributeTarget.setTarget(target);
		attributeTarget.setAttributeName(attributeName);
		
		result.setTarget(attributeTarget);
		
		traceability.getTraces().add(result);
		
		return result;
	}
	
	public Traceability createTraceabilityEObject() {
		return TraceabilityFactory.eINSTANCE.createTraceability();
	}
	
	public Trace createTraceEObject() {
		return TraceabilityFactory.eINSTANCE.createTrace();
	}
	
	public EObjectSource createEObjectSourceEObject() {
		return TraceabilityFactory.eINSTANCE.createEObjectSource();
	}
	
	public JavaObjectSource createJavaObjectSourceEObject() {
		return TraceabilityFactory.eINSTANCE.createJavaObjectSource();
	}
	
	public ReferenceSource createReferenceSourceEObject() {
		return TraceabilityFactory.eINSTANCE.createReferenceSource();
	}
	
	public AttributeSource createAttributeSourceEObject() {
		return TraceabilityFactory.eINSTANCE.createAttributeSource();
	}
	
	public EObjectTarget createEObjectTargetEObject() {
		return TraceabilityFactory.eINSTANCE.createEObjectTarget();
	}
	
	public ReferenceTarget createReferenceTargetEObject() {
		return TraceabilityFactory.eINSTANCE.createReferenceTarget();
	}
	
	public AttributeTarget createAttributeTargetEObject() {
		return TraceabilityFactory.eINSTANCE.createAttributeTarget();
	}
	
	// TODO JAVADOC, TESZT
	// TODO Lehetne egy mintával is...
	public Set<Trace> getTraces(Map<String, Object> sources, Collection<TransformationRuleDescriptor> transformationRuleDescriptors) {
		Set<Trace> result = Sets.newHashSet();
		
		for (TransformationRuleDescriptor transformationRuleDescriptor : transformationRuleDescriptors) {
			result.addAll(getTraces(sources, transformationRuleDescriptor.getId()));
		}
		
		return result;
	}
	
	// TODO teszt, javadoc
	public Set<Trace> getTraces(EObject target) {
		Set<Trace> result = Sets.newHashSet();
		
		try {
			GetTraceByEObjectTargetQuerySpecification querySpecification = GetTraceByEObjectTargetQuerySpecification.instance();
			
			GetTraceByEObjectTargetMatch emptyMatch = querySpecification.newEmptyMatch();
			emptyMatch.setTargetEObject(target);
			
			for (GetTraceByEObjectTargetMatch match : querySpecification.getMatcher(engine).getAllMatches(emptyMatch)) {
				result.add(match.getTrace());
			}
		} catch (ViatraQueryException e) {
			e.printStackTrace();
			
			// TODO
		}

		return result;
	}
	
	// TODO teszt, javadoc
	public Set<Trace> getTraces(EObject owner, EObject target, String referenceName) {
		Set<Trace> result = Sets.newHashSet();
		
		try {
			GetTraceByReferenceTargetQuerySpecification querySpecification = GetTraceByReferenceTargetQuerySpecification.instance();
			
			GetTraceByReferenceTargetMatch emptyMatch = querySpecification.newEmptyMatch();
			emptyMatch.setOwnerEObject(owner);
			emptyMatch.setTargetEObject(target);
			emptyMatch.setReferenceName(referenceName);
			
			for (GetTraceByReferenceTargetMatch match : querySpecification.getMatcher(engine).getAllMatches(emptyMatch)) {
				result.add(match.getTrace());
			}
		} catch (ViatraQueryException e) {
			e.printStackTrace();
			
			// TODO
		}

		return result;
	}
	
	// TODO teszt, javadoc
	public Set<Trace> getTraces(EObject owner, Object target, String attributeName) {
		Set<Trace> result = Sets.newHashSet();
		
		try {
			GetTraceByAttributeTargetQuerySpecification querySpecification = GetTraceByAttributeTargetQuerySpecification.instance();
			
			GetTraceByAttributeTargetMatch emptyMatch = querySpecification.newEmptyMatch();
			emptyMatch.setOwnerEObject(owner);
			emptyMatch.setTargetJavaObject(target);
			emptyMatch.setAttributeName(attributeName);
			
			for (GetTraceByAttributeTargetMatch match : querySpecification.getMatcher(engine).getAllMatches(emptyMatch)) {
				result.add(match.getTrace());
			}
		} catch (ViatraQueryException e) {
			e.printStackTrace();
			
			// TODO
		}

		return result;
	}
	
	/**
	 * Get Trace instances from the traceability model by ruleDescriptorId and sources.
	 * @param sources The source Objects / EObjects, that belong to the trace
	 * @param ruleDescriptorId The id of the RuleDescriptor instance, that created the Trace
	 * @return Trace instances, which have the given id and sources
	 */
	// TODO jobb lenne settel...
	public List<Trace> getTraces(Map<String, Object> sources, Long ruleDescriptorId) {
		List<Trace> result = Lists.newArrayList();

		try {
			TraceQuerySpecification querySpecification = null; 
					
			if (traceQuerySpecifications.get(sources.size()) != null) {
				querySpecification = traceQuerySpecifications.get(sources.size());
			} else {
				querySpecification = new TraceQuerySpecification(sources.size());
				
				traceQuerySpecifications.put(sources.size(), querySpecification);
			}
			
			int i = 0;
			GenericPatternMatch emptyMatch = querySpecification.newEmptyMatch();
			for (Entry<String, Object> source : sources.entrySet()) {
				emptyMatch.set(TraceQuerySpecification.TracePQuery.getSourceParameterName(i), source.getValue());
				emptyMatch.set(TraceQuerySpecification.TracePQuery.getPatternParameterNameParameterName(i), source.getKey());
				
				i++;
			}
			
			if (ruleDescriptorId != null) {
				emptyMatch.set(TraceQuerySpecification.TracePQuery.PARAMETER_RULE_DESCRIPTOR_ID, ruleDescriptorId);
			}

			
			Collection<GenericPatternMatch> matches = querySpecification.getMatcher(engine).getAllMatches(emptyMatch);
			for (GenericPatternMatch match : matches) {
				result.add((Trace) match.get(TraceQuerySpecification.TracePQuery.PARAMETER_TRACE));
			}
		} catch (ViatraQueryException e) {
			// TODO hibakezelés...
		}
		
		return result;
	}
	
	/**
	 * Get TraceTarget instances of the matching traces.
	 * @param sources Source objects of the trace
	 * @param ruleDescriptorId Id of the RuleDescriptor instance, that created the trace
	 * @return TraceTarget instances of the matching traces
	 */
	public List<TraceTarget> getTargets(Map<String, Object> sources, Long ruleDescriptorId) {
		List<TraceTarget> result = Lists.newArrayList();
		
		List<Trace> traces = getTraces(sources, ruleDescriptorId);
		for (Trace trace : traces) {
			result.add(trace.getTarget());
		}
		
		return result;
	}
	
	/**
	 * This method removes the given sources if it's needed from the traceability model,
	 * 	or decrement the referenced value of the source.
	 * @param trace The trace, that the sources belongs to
	 * @param sources The sources to remove
	 * @param hidden The sources are hidden or not
	 */
	public void removeSources(Trace trace, Map<String, Object> sources, boolean hidden) {
		GetTraceSourceMatch match = null;
		GetTraceSourceMatch emptyMatch = null;
		GetTraceSourceQuerySpecification querySpecification = null;
		
		try {
			querySpecification = GetTraceSourceQuerySpecification.instance();
			
			for (Entry<String, Object> source : sources.entrySet()) {
				emptyMatch = querySpecification.newEmptyMatch();
				emptyMatch.setTrace(trace);
				emptyMatch.setHidden(hidden);
				emptyMatch.setPatternParameterName(source.getKey());
				emptyMatch.setSource(source.getValue());

				match = querySpecification.getMatcher(engine).getOneArbitraryMatch(emptyMatch);
				if (match != null && match.getTraceSource() != null) {
					if (match.getTraceSource().getReferenced() > 1) {
						match.getTraceSource().setReferenced(match.getTraceSource().getReferenced()-1);
					} else {
						EcoreUtil.remove(match.getTraceSource());
					}
				}
			}
		} catch (ViatraQueryException e) {
			e.printStackTrace();
			// TODO
		}
	}
	
	/**
	 * Removes the given Trace instance from the traceability model.
	 * @param trace The Trace instance to remove
	 */
	public void removeTrace(Trace trace) {
		EcoreUtil.remove(trace);
	}
}
