package org.rapp.roger.jenkinsplugins;

import hudson.model.Cause;

import java.io.Serializable;
import java.util.List;

public class PropertyTriggerCause extends Cause implements Serializable {
	
	private static final long serialVersionUID = 7099355728482804805L;
	private String strCause;

	public PropertyTriggerCause(String strCause) {
		this.strCause = strCause;
	}

	public PropertyTriggerCause(List<String> list) {
		StringBuffer buf = new StringBuffer();
		for (String s : list) {
			buf.append(s + "\n");
		}
		this.strCause = buf.toString();
	}

	@Override
	public String getShortDescription() {
		return strCause;
	}

}
