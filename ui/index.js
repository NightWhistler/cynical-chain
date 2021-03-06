var colourMapping = {
    "Genesis": "skyblue",
    "node1": "palegreen",
    "node2": "orange",
    "node3": "tomato",
    "node4": "khaki"
}

function loadJSON(url, callback, failureCallback) {   

    var xobj = new XMLHttpRequest();
    xobj.overrideMimeType("application/json");
    xobj.open('GET', url, true); // Replace 'my_data' with the path to your file

    xobj.onreadystatechange = function () {
          if (xobj.readyState == 4 && xobj.status == "200") {
            callback(xobj.responseText);
          } else if ( xobj.status != "200" ) {
            failureCallback(xobj.status);
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

    var nodeName = "node" + (nodeNum+1);
    var scoreNodeName = "score_" + nodeName;
    var container = document.getElementById(nodeName);
    var scoreNode = document.getElementById(scoreNodeName);

    loadJSON(url, function(response) {
        var blocks = JSON.parse(response).reverse();
        console.log( "Got " + blocks.length + " blocks for node " + nodeNum);

        var dot = "digraph { ";
        dot += "rankdir=\"RL\";node[style=filled;shape=record];\n";

        for ( var i=0; i < blocks.length; i++ ) {
            var block = blocks[i];
            var blockHash = block.hash.substring(0,7);
            var prevHash = block.previousHash.substring(0,7);

            dot += blockName(i) + "[label=\" { index: " + block.index + "| foundBy: " + block.foundBy + " | nonce: " + block.nonce + "} | { prev-hash: " + prevHash + " |  hash: " + blockHash + "} | timestamp: " + block.timestamp + "| " + messages(block) + "\";fillcolor=" + colourMapping[block.foundBy] +"];\n"

            if ( i < blocks.length -1 ) {
                dot += blockName(i+1) + " -> " + blockName(i) + ";\n";
            }
        }

        dot += "}";

        var image = Viz(dot, { format:  "png-image-element"});
        var latestHash = blocks[blocks.length -1].hash;

        if ( container.children.length == 0 ) {
            container.appendChild(image);
        } else if ( container.getAttribute('latestHash') != latestHash ) {
            container.replaceChild(image, container.children[0]);
        }
        container.setAttribute('latestHash', latestHash);
        scoreNode.innerHTML = "Node " + (nodeNum +1) + " (" + score(blocks, nodeName) + ")";

        // var dotNodeName = nodeName + "_dot";
        // document.getElementById(dotNodeName).innerHTML = "<pre>" + dot + "</pre>";
    }, function(status) {
        if ( container.children.length > 0 ) {
            container.removeChild(container.children[0]);
            container.setAttribute('latestHash', '');
            scoreNode.innerHTML = '';
        }
    });
}

function drawGraph(dotInput) {
    var image = Viz(dotInput, { format: "svg" });
    document.getElementById("graph").innerHTML = image;
}

