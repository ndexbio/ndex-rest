package org.ndexbio.common.cx;

import java.util.Collection;

import org.cxio.core.interfaces.AspectElement;

public class CXAspectFragment {
	private String aspectName;
	
	private Collection<AspectElement> elements;
	
	public CXAspectFragment(String aspectName, Collection<AspectElement> aspectElements) {
		this.aspectName = aspectName;
		this.elements = aspectElements;
	}

	public String getAspectName() {
		return aspectName;
	}

	public void setAspectName(String aspectName) {
		this.aspectName = aspectName;
	}

	public Collection<AspectElement> getElements() {
		return elements;
	}

	public void setElements(Collection<AspectElement> elements) {
		this.elements = elements;
	}
	
	
}
