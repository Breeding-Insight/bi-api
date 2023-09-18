package org.breedinginsight.brapi.v2;

import org.brapi.v2.model.BrAPIWSMIMEDataTypes;
import org.brapi.v2.model.core.BrAPIService;
import org.brapi.v2.model.core.BrAPIService.MethodsEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServiceBuilder extends ArrayList<BrAPIService>{
	private static final long serialVersionUID = 1L;
	private String path = "";
	private String base = "";
	private List<MethodsEnum> methods = new ArrayList<>();
	private List<String> versions = new ArrayList<>();

	public ServiceBuilder setBase(String base) {
		this.path = base;
		this.base = base;
		this.methods.clear();
		return this;
	}
	public ServiceBuilder setPath(String newPath) {
		build();
		
		String[] pathParts = this.path.split("/");
		String oldPath = pathParts[pathParts.length - 1].replaceAll("\\{", "\\\\{").replaceAll("\\}", "\\\\}");
		this.path = this.path.replaceFirst(oldPath, newPath);
		this.methods.clear();
		return this;
	}
	public ServiceBuilder addPath(String path) {
		build();
		this.path = this.path + '/' + path;
		this.methods.clear();
		return this;
	}
	
	public ServiceBuilder PUT() {
		methods.add(MethodsEnum.PUT);
		return this;
	}
	
	public ServiceBuilder POST() {
		methods.add(MethodsEnum.POST);
		return this;
	}

	public ServiceBuilder GET() {
		methods.add(MethodsEnum.GET);
		return this;
	}

	public ServiceBuilder versions(String ... versions) {
		this.versions = Arrays.asList(versions);
		return this;
	}

	public ServiceBuilder build() {
		if(path != null && !path.isEmpty() && methods != null && !methods.isEmpty()) {
			this.add(buildService(path, methods));
		}
		return this;
	}
	public ServiceBuilder withSearch() {
		build();
		this.add(buildService("search/" + base, Arrays.asList(MethodsEnum.POST)));
		this.add(buildService("search/" + base + "/{searchResultsDbId}", Arrays.asList(MethodsEnum.GET)));
		return this;
	}
	public BrAPIService buildService(String path, List<MethodsEnum> methods) {
		BrAPIService service = new BrAPIService();
		service.addDataTypesItem(BrAPIWSMIMEDataTypes.APPLICATION_JSON);
		service.setMethods(new ArrayList<>(methods));
		service.setVersions(new ArrayList<>(this.versions));
		service.setService(path);
		return service;
	}
}
