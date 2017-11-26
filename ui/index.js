var colourMapping = {
    "Genesis": "skyblue",
    "node1": "palegreen",
    "node2": "orange",
    "node3": "tomato",
    "node4": "khaki"
}

function loadJSON(url, callback) {   

    var xobj = new XMLHttpRequest();
    xobj.overrideMimeType("application/json");
    xobj.open('GET', url, true); // Replace 'my_data' with the path to your file

    xobj.onreadystatechange = function () {
          if (xobj.readyState == 4 && xobj.status == "200") {
            callback(xobj.responseText);
          }
    };
    xobj.send(null);  
}

function score(blocks, nodeName) {
    return blocks.filter( function(b) { return b.foundBy == nodeName; } ).length;
}

function blockName(index) {
    return index == 0 ? "Genesis" : "Block" + index;
}

function messages(block) {
    return block.messages.map( function(m) { return "- " + m.data; } ).join("\\n");
}

function loadBlockChains() {
    for (var i=0; i < 4; i++) {
        loadBlocks(i);
    }
}

function loadBlocks(nodeNum) {

    var port = 9000 + nodeNum;
    var url = "http://localhost:" + port + "/blocks";

    loadJSON(url, function(response) {
        var blocks = JSON.parse(response).reverse();
        console.log( "Got " + blocks.length + " blocks for node " + nodeNum);

        var dot = "digraph { ";
        dot += "rankdir=\"LR\";node[style=filled;shape=record];\n";

        for ( var i=0; i < blocks.length; i++ ) {
            var block = blocks[i];

            dot += blockName(i) + "[label=\"index: " + block.index + "| foundBy: " + block.foundBy + "| " + messages(block) + "\",fillcolor=" + colourMapping[block.foundBy] +"];\n"

            if ( i < blocks.length -1 ) {
                dot += blockName(i) + " -> " + blockName(i+1) + ";\n";
            }
        }

        dot += "}";

        var image = Viz(dot, { format:  "png-image-element"});
        var nodeName = "node" + (nodeNum+1);
        var scoreNodeName = "score_" + nodeName;

        document.getElementById(scoreNodeName).innerHTML = score(blocks, nodeName);
        document.getElementById(nodeName).appendChild(image);

        // var dotNodeName = nodeName + "_dot";
        // document.getElementById(dotNodeName).innerHTML = "<pre>" + dot + "</pre>";
    });
}

function drawGraph(dotInput) {
    var image = Viz(dotInput, { format: "svg" });
    document.getElementById("graph").innerHTML = image;
}

