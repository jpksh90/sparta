//"use strict";

/* Constants */
var DATA_FILE_PATH = "API Graph Data.json";
var PATH_DELIMITER = "/";
var PACKAGE_DELIMITER = ".";

/* Global Variables */
var svg;
var color

var x_children;
var x_parent;
var y_children;
var y_parent;

var apiTree;
var directoryTree;

var apiViewActive;

var treemap;

/*
 * Initializes the visualization.
 */
function initialize() {
	parseDataFile(DATA_FILE_PATH, function(trees) {
		var width = window.innerWidth,
	    height = window.innerHeight;
		
		
		x_children = d3.scale.linear().range([0, width]);
		y_children = d3.scale.linear().range([60, height]);
		x_parent = d3.scale.linear().range([0, width]);
		y_parent = d3.scale.linear().range([0, 60]);
		
		apiTree = trees.apiTree;
		directoryTree = trees.directoryTree;
		
		apiViewActive = true;
		
		displayTreemap();
		
		addViewByButtonClickListeners();
	});
}

function addViewByButtonClickListeners() {
		var width = window.innerWidth,
	    height = window.innerHeight;
	d3.select("#viewByApiButton").on("click", function() {
		if (!apiViewActive) {
			apiViewActive = true;
			x_children = d3.scale.linear().range([0, width]);
		y_children = d3.scale.linear().range([60, height]);
		x_parent = d3.scale.linear().range([0, width]);
		y_parent = d3.scale.linear().range([0, 60]);
		
			displayTreemap();
		}
	});
	
	d3.select("#viewByFileButton").on("click", function() {
		if (apiViewActive) {
			apiViewActive = false;
			x_children = d3.scale.linear().range([0, width]);
		y_children = d3.scale.linear().range([60, height]);
		x_parent = d3.scale.linear().range([0, width]);
		y_parent = d3.scale.linear().range([0, 60]);
		
			displayTreemap();
		}
	})
}

function displayTreemap() {
	d3.select("#descriptiveText").text("");
	// update the "View by" selector
	d3.select("#viewByApiButton").classed("selected", function() {
		return apiViewActive; 
	});
	d3.select("#viewByFileButton").classed("selected", function() {
		return !apiViewActive; 
	});
	
	
	
	var width = window.innerWidth,
	    height = window.innerHeight;
	    
	svg = d3.select("#viewport") //TODO: don't make a global variable
		.attr("width", width)
		.attr("height", height);
		
	// TODO: ugly way to clear treemap; fix
	svg.text(" ");
	var cell;
	if (apiViewActive) {
		color = d3.scale.category20(); //TODO: fix
		treemap = d3.layout.treemap()
			.size([width, height])
			.children(function (d) {
				return d3.entries(d.value.contents); //TODO: clean up
			})
			.value(function (d) {
				return d.value.fileUsageCount || d.value.referencingFiles.length;
			}).sticky(true);
		 cell = svg.data(d3.entries(apiTree)).selectAll("g")
	  .data(treemap)
	.enter().append("g")
	  .attr("class", function(d) {return d.depth == 0 ? "parent" : "child"})
	  .attr("transform", function(d) {
	  	return "translate(" + x_children(d.x) + "," + y_children(d.y) + ")"; })
	  .attr("visibility", function(d) {return d.depth == 0 ? "visible" : "hidden"})
	  .on("click",onClick)
		  ;
		  
		  cell.append("rect")
	  .attr("width", function(d) { return d.dx; })
	  .attr("height", function(d) { return d.dy; })
	  .style("fill", function(d) {return color(d.data.value.fileUsageCount || d.data.value.referencingFiles.length);})
		  ;
		  
		  cell.append("foreignObject")
.attr("width", function(d) { return d.dx; })
	  .attr("height", function(d) { return d.dy; })
	  .append("xhtml:div")
	  .attr("class", "label")
	  .style("font-size", function(d) {
	  	return d3.scale.linear().domain([0, window.innerHeight * window.innerWidth]).range([1, 3])(d.dx * d.dy) + "em";
	  })
	.text(function(d) { return d.data.key + " (referenced by " +
			(d.data.value.fileUsageCount || d.data.value.referencingFiles.length) + " files)"; });
	} else { // show the File view
		color = d3.scale.category20c();
		treemap = d3.layout.treemap()
			.size([width, height])
			.children(function (d) {
				return d3.entries(d.value.contents); //TODO: clean up
			})
			.value(function (d) {
				if (d.value.type === "directory") {
					return d.value.apiUsageCount;
				} else {
					return d.value.apisUsed.length;
				}
			}).sticky(true);
		 cell = svg.data(d3.entries(directoryTree)).selectAll("g")
	  .data(treemap)
	.enter().append("g")
	  .attr("class", function(d) {return d.depth == 0 ? "parent" : "child"})
	  .attr("transform", function(d) { return "translate(" + x_children(d.x) + "," + y_children(d.y) + ")"; })
	  .attr("visibility", function(d) {return d.depth == 0 ? "visible" : "hidden"})
	  .on("click",onClick)
		  ;
		  
		  cell.append("rect")
	  .attr("width", function(d) { return d.dx; })
	  .attr("height", function(d) { return d.dy; })
	  .style("fill", function(d) {return color(d.data.value.apiUsageCount || d.data.value.apisUsed.length);})
;
			
					  cell.append("foreignObject")
.attr("width", function(d) { return d.dx; })
	  .attr("height", function(d) { return d.dy; })
	  .append("xhtml:div")
	  .attr("class", "label")
	  .style("font-size", function(d) {
	  	return d3.scale.linear().domain([0, window.innerHeight * window.innerWidth]).range([1, 3])(d.dx * d.dy) + "em";
	  })
	.text(function(d) { return d.data.key + " (uses " +
			(d.data.value.apiUsageCount || d.data.value.apisUsed.length) + " APIs)"; });
	}		  
}

function onClick(d,i) {
	var path = [d.data.key];
var parent = d.parent;
	while (parent) {
		path.unshift(parent.data.key);
		parent = parent.parent;
	}
		d3.select("#descriptiveText")
			.text(path.join(" > "));
	if (d.children) {
	var clickClass = d3.select(this).attr("class");
	var datum = (clickClass == "parent" && d.depth != 0) ? d.parent : d;
	
	x_parent.domain([d.x, d.x + d.dx]);
    y_parent.domain([d.y, d.y + d.dy]);
	
	if(d.children && d.depth != 0)
		svg.selectAll("g")
			.attr("visibility","hidden")
			;
	
	svg.selectAll("g")
		.filter(function(d) {
			return d == datum && d.children;
		})
		.attr("class","parent")
		.attr("visibility","visible")
		.transition().duration(750)
		.attr("transform", function(d) { return "translate(" + x_parent(d.x) + "," + y_parent(d.y) + ")"; })
		.select("rect")
			.attr("width", function(d) { return x_parent(d.dx+d.x) - x_parent(d.x); })
      		.attr("height", function(d) { return y_parent(d.dy+d.y) - y_parent(d.y); })
			;
			
	svg.selectAll("g")
		.filter(function(d) {
			return d == datum && d.children;
		})
		.select("foreignObject")
			.attr("width", function(d) { return x_parent(d.dx+d.x) - x_parent(d.x); })
      		.attr("height", function(d) { return y_parent(d.dy+d.y) - y_parent(d.y); })
			;

	x_children.domain([datum.x, datum.x + datum.dx]);
    y_children.domain([datum.y, datum.y + datum.dy]);
			
    svg.selectAll("g")
	.filter(function(d){
			return d.parent ? d.parent == datum : d.depth == 1;
	})
	.transition().duration(750)
	.attr("class","child")
	.attr("visibility","visible")
	.attr("transform", function(d) { return "translate(" + x_children(d.x) + "," + y_children(d.y) + ")"; })
	.select("rect")
		.attr("width", function(d) { return x_children(d.dx+d.x) - x_children(d.x); })
		.attr("height", function(d) { return y_children(d.dy+d.y)-y_children(d.y); })
		;
		
			svg.selectAll("g")
		.filter(function(d) {
			return d.parent ? d.parent == datum : d.depth == 1;
		})
		.select("foreignObject")
		.attr("width", function(d) { return x_children(d.dx+d.x) - x_children(d.x); })
		.attr("height", function(d) { return y_children(d.dy+d.y)-y_children(d.y); })
			;
			

	} else { // at child
		var treeNode = d.data.value;
		d3.select("#dialog").html(" ");
		
		d3.select("#dialog").append("h1")
			.text(d.data.key);
			
			d3.select("#dialog").append("h2")
			.text("Type: " + treeNode.type);
		if (apiViewActive) {
			d3.select("#dialog").append("h2")
			.text("Used by");
			
			var list = d3.select("#dialog").append("ul");
			
		treeNode.referencingFiles.forEach(function (file) {
			list.append("li").text(file.file.name + " (line " + file.lineUsingApi + ")");
		})
	} else {
		d3.select("#dialog").append("h2")
			.text("APIs Used");
			
			var list = d3.select("#dialog").append("ul");
			
		treeNode.apisUsed.forEach(function (api) {
			list.append("li").text(api.api.name + " (type: " + api.api.type) + ")";});
	}
		
		d3.select("#dialog").style("display", "block");
		
		d3.select("#dialog").append("a").attr("class", "closeLink").text("Close").on("click", function() {
			d3.select("#dialog").style("display", "none");
		})
	}
}

function getTextDimensions(text) {
	var testElement = document.createElement("span");
	testElement.className = "testElement";
	testElement.textContent = text;
	
	document.body.appendChild(testElement);
	
	var width = testElement.clientWidth;
	var height = testElement.clientHeight;
	
	document.body.removeChild(testElement);
	return {width: width, height: height};
}

function parseDataFile(dataFilePath, callbackFunction) {
	d3.json(dataFilePath, function(apiUsages) {
		var directoryTree = {};
		var apiTree = {};
		
		// need to create directory tree and API tree
		apiUsages.forEach(function (apiUsage) {
			// get the appropriate in directoryTree for the current filename
			// or create the appropriate node if it doesn't exist
			var fileNode = makeDirectoryTreeNodesFromPath(directoryTree,
					apiUsage.filename);
			var apiNode = makeApiTreeNodesFromName(apiTree, apiUsage.useby,
					apiUsage.usebykind);
					
			fileNode.apisUsed.push(new ApiUsage(apiNode, apiUsage.line));
			apiNode.referencingFiles.push(new FileUsage(fileNode, apiUsage.line));
		});
		
		setApiUsageCounts(directoryTree);
		setFileUsageCounts(apiTree);
		
		callbackFunction({
			"apiTree": apiTree,
			"directoryTree": directoryTree
		});
	});
}

function setApiUsageCounts(directoryTree) {
	for (var directory in directoryTree) {
		setApiUsageCountsHelper(directoryTree[directory]);
	}
}

function setApiUsageCountsHelper(directoryNode) {
	if (directoryNode.type == "file") {
		return directoryNode.apisUsed.length;
	}
	
	var count = 0;
	for (var childNode in directoryNode.contents) {
		count += setApiUsageCountsHelper(directoryNode.contents[childNode]);
	}
	directoryNode.apiUsageCount = count;
	return count;
}

function setFileUsageCounts(apiTree) {
	for (var apiName in apiTree) {
		setFileUsageCountsHelper(apiTree[apiName]);
	}
}

function setFileUsageCountsHelper(apiNode) {
	if (apiNode.isLeafNode) {
		return apiNode.referencingFiles.length;
	}
	
	var count = 0;
	for (var childNode in apiNode.contents) {
		count += setFileUsageCountsHelper(apiNode.contents[childNode]);
	}
	apiNode.fileUsageCount = count;
	return count;
}

function makeDirectoryTreeNodesFromPath(tree, path) {
	var pathPieces = path.split(PATH_DELIMITER);
	var pathPiecesLength = pathPieces.length;
	
	var fileNode;
	var currentRootNode = tree;
	
	for (var i = 0; i < pathPiecesLength; i += 1) {
		var pathPiece = pathPieces[i];
		if (pathPiece !== ".") { // ignore the "." folder
			var atLastPiece = (i === (pathPiecesLength - 1));
			
			var nodeType;
			var isLeafNode;
			if (atLastPiece) {
				nodeType = "file";
				isLeafNode = true;
			} else { // not at the last piece
				nodeType = "directory";
				isLeafNode = false;
			}
			
			if (!currentRootNode[pathPiece]) { // node doesn't exist; create one
				currentRootNode[pathPiece] = new DirectoryTreeNode(pathPiece, nodeType, isLeafNode);
			}
			
			if (atLastPiece) {
				fileNode = currentRootNode[pathPiece];
			} else { // not at last piece
				currentRootNode = currentRootNode[pathPiece].contents;
			}
		}
	}
	
	console.assert(fileNode !== undefined && fileNode !== null);
	return fileNode;
}

//TODO: code similar to makeDirectoryTreeNodesFromPath; factor out common code
function makeApiTreeNodesFromName(tree, name, nameType) {
	var namePieces = name.split("(")[0].split(PACKAGE_DELIMITER);
	var namePiecesLength = namePieces.length;
	
	var apiNode;
	var currentRootNode = tree;
	
	for (var i = 0; i < namePiecesLength; i += 1) {
		var namePiece = namePieces[i];
		var atSecondToLastPiece = (i === (namePiecesLength - 2));
		var atLastPiece = (i === (namePiecesLength - 1));
		
		var nodeType;
		var isLeafNode = false;
		if (atLastPiece) {
			nodeType = nameType;
			isLeafNode = true;
		} else if (atSecondToLastPiece && (nameType === "METHOD" || nameType === "CONSTRUCTOR" ||
				nameType === "FIELD")){
			nodeType = "CLASS";
		} else {
			nodeType = "PACKAGE"
		}
		
		if (!currentRootNode[namePiece]) { // node doesn't exist; create one
			currentRootNode[namePiece] = new ApiTreeNode(namePiece, nodeType, isLeafNode);
		}
		
		
		if (atLastPiece) {
			apiNode = currentRootNode[namePiece];
		} else { // not at last piece
			currentRootNode = currentRootNode[namePiece].contents;
		}
	}
	
	console.assert(apiNode !== undefined && apiNode !== null);
	return apiNode;
}

//TODO: make a Node class and have the two node types inherit from it
function DirectoryTreeNode(name, type, isLeafNode) {
	this.name = name;
	this.type = type;
	this.isLeafNode = isLeafNode;
	
	if (type === "directory") {
		this.apiUsageCount = 0;
		this.contents = {};
	} else {
		console.assert(type === "file");
		this.apisUsed = [];
	}
}

function ApiTreeNode(name, type, isLeafNode) {
	this.name = name;
	this.type = type;
	this.fileUsageCount = 0;
	this.isLeafNode = isLeafNode;
	
	this.referencingFiles = [];
	
	if (type === "PACKAGE" || type === "CLASS" || type === "INTERFACE") {
		this.contents = {};
	}
}

function FileUsage(file, lineUsingApi) {
	this.file = file;
	this.lineUsingApi = lineUsingApi;
}

function ApiUsage(api, lineUsingApi) {
	this.api = api;
	this.lineUsingApi = lineUsingApi;
}

window.onload = initialize;