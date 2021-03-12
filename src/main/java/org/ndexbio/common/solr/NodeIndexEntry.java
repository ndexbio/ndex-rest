package org.ndexbio.common.solr;

import java.util.Collection;
import java.util.TreeSet;

public class NodeIndexEntry {

	private Long id;
	private String name;
	//private boolean memberIsToBeIndexed;
	private Collection<String> represents;
	private Collection<String> aliases;
	//private Collection<String> members;
	private Collection<String> text;
	

	public NodeIndexEntry() {
		setId(null);
		setName(null);
		setRepresents(new TreeSet<>());
		setAliases(new TreeSet<>());
		//setMembers(new TreeSet<>());
	//	memberIsToBeIndexed = false;
	}
	
	public NodeIndexEntry(Long nodeId, String nodeName) {
		setId(nodeId);
		setName(nodeName);
		setRepresents(new TreeSet<>());
		setAliases(new TreeSet<>());
	//	setMembers(new TreeSet<>());
	//	memberIsToBeIndexed = false;
		text = new TreeSet<>();
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

/*	public Collection<String> getMembers() {
		return members;
	}

	public void setMembers(Collection<String> members) {
		this.members = members;
	}

	public boolean isMemberIsToBeIndexed() {
		return memberIsToBeIndexed;
	}

	public void setMemberIsToBeIndexed(boolean memberIsToBeIndexed) {
		this.memberIsToBeIndexed = memberIsToBeIndexed;
	}*/
	
	
	public Collection<String> getText() {
		return text;
	}

	/*public void setText(Collection<String> text) {
		this.text = text;
	} */
	
	public void addText(String txt) {
		text.add(txt);
	}

}
