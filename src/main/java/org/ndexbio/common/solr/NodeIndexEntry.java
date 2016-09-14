package org.ndexbio.common.solr;

import java.util.Collection;
import java.util.TreeSet;

public class NodeIndexEntry {

	private Long id;
	private String name;
	private Collection<String> represents;
	private Collection<String> aliases;
	
	public NodeIndexEntry() {
		setId(null);
		setName(null);
		setRepresents(new TreeSet<>());
		setAliases(new TreeSet<>());
	}
	
	public NodeIndexEntry(Long nodeId, String nodeName) {
		setId(nodeId);
		setName(nodeName);
		setRepresents(new TreeSet<>());
		setAliases(new TreeSet<>());
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Collection<String> getAliases() {
		return aliases;
	}

	public void setAliases(Collection<String> aliases) {
		this.aliases = aliases;
	}

	public Collection<String> getRepresents() {
		return represents;
	}

	public void setRepresents(Collection<String> represents) {
		this.represents = represents;
	}
	
}
