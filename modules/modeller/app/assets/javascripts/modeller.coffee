# Point of origin, keep track where we are on the canvas
paperx = 0
papery = 0
paperdx = 0
paperdy = 0

# To deal with numbers containing Tuktu Strings, we need some number inputs
intInputTester = document.createElement('input')
intInputTester.type = 'number'
intInputTester.step = '1'
floatInputTester = document.createElement('input')
floatInputTester.type = 'number'
floatInputTester.step = 'any'

# Unfortunately the svg element doesn't have clientWidth, clientHeight
# So we have to take it from its parent and deduct div-padding and svg-border
# This means the following values have to be changed if the layout changes!
paperw = -> flowchart.clientWidth - 34
paperh = -> flowchart.clientHeight - 4
paper = Raphael('flowchart', '100%', '100%')
flowchart = document.getElementById('flowchart')
flowchart.firstChild.style.border = '2px #666 solid'
flowchart.firstChild.style.borderRadius = '5px'

# Make the flowchart height window height -10px; change it if window is resized
flowchart.style.height = (window.innerHeight - flowchart.parentNode.offsetTop - 10) + 'px'
$( window ).resize( ->
	flowchart.style.height = (window.innerHeight - flowchart.parentNode.offsetTop - 10) + 'px'
	paper.setViewBox(paperx, papery, paperw(), paperh()))


# Create a huge background rect that will capture panning
background = paper.rect(-10, -10, 1e6, 1e6)
background.attr({'fill': 'white'})
# On drag change viewbox according to grid
bgDrag = (dx, dy) ->
	paperdx = Math.round(dx / grid) * grid
	paperdy = Math.round(dy / grid) * grid
	paper.setViewBox(paperx - paperdx, papery - paperdy, paperw(), paperh())
	background.attr({'x': paperx - paperdx - 10, 'y': papery - paperdy - 10})

bgUp = ->
	paperx -= paperdx
	papery -= paperdy
	paperdx = 0
	paperdy = 0

background.drag(bgDrag, bgUp, bgUp)

allNodes =
	generators: []
	processors: []
selected = null

globalId = 0
grid = 10
_delay = 100

# Returns the first parent node of elem that is a Raphael element or null if there is none
getRaphaelParent = (elem) ->
	while elem? and elem.raphael isnt true
		elem = elem.parentNode
	elem

# Click on svg deselects selected element
mClickSVG = ->
	# if something is selected and no Raphael element is clicked, deselect
	selected.deselect() if selected?

background.click(mClickSVG)

# Store the current position in data for all children of node
mDownBox = (node) -> ->
	document.activeElement.blur()
	node.select() if node isnt selected
	for elem in node.children
		if elem.type is 'circle'
			elem.data('cx', elem.attr('cx'))
			elem.data('cy', elem.attr('cy'))
		else
			elem.data('x',  elem.attr('x'))
			elem.data('y',  elem.attr('y'))

# Move all children of node according to grid and stored position
mDragBox = (node) -> (dx, dy) ->
	dx = Math.round(dx / grid) * grid
	dy = Math.round(dy / grid) * grid
	for elem in node.children
		if elem.type is 'circle'
			elem.attr('cx', elem.data('cx') + dx)
			elem.attr('cy', elem.data('cy') + dy)
		else
			elem.attr('x', elem.data('x') + dx)
			elem.attr('y', elem.data('y') + dy)
	node.redrawConnections()

# Start drawing a path from access point to mouse pointer
mDownAccess = ->
	this.data('line', new Connection(this.data('node')))

# If mouse is upped over raphael element, connect if possible, else remove line
mUpAccess = (event) ->
	this.data('line').connectTo(event.target)

# Update path on drag
mDragAccess = (dx, dy, x, y, event) ->
	to = getRaphaelParent(event.target)
	if to?
		elem = paper.getById(to.raphaelid).data('node')
		if elem? and elem.canBeConnected is true
			this.data('line').redraw('C', elem.getTargetPoint())
		else
			this.data('line').redraw('l', [dx, dy])
	else
		this.data('line').redraw('l', [ dx, dy])


# Highlight hovered connections to make them easier clickable
mHoverInConn = (conn) -> ->
	conn.highlight()
	conn.from.highlight()
	conn.to.highlight()

# Unhighlight if nothing is slected
mHoverOutConn = (conn) -> ->
	if conn.from isnt selected and conn.to isnt selected and conn isnt selected
		conn.unhighlight()
		conn.from.unhighlight()
		conn.to.unhighlight()

mMouseDownConn = (conn) -> ->
	document.activeElement.blur()
	conn.select() if conn isnt selected

class Connection
	@line = null

	constructor: (@from, @to = null) ->
		@line = paper.path('M0,0l0,0')
		@line.attr('stroke-width': 3)
		@line.insertAfter(background)
		@highlight()
		@connect(@to) if @to?
		this

	destructor: ->
		@deselect() if this is selected
		@line.remove() if @line?
		delete @line
		if @from? and @to?
			@from.removeSuccessor(@to)
			@to.removePredecessor(@from)
		delete @from
		delete @to
		generateConfig()

	connect: (elem) ->
		if elem is undefined or elem.canBeConnected isnt true or elem is @from or elem.id of @from.successors
			# Connection was not dropped over a node that can't be connected;
			# or that is from, or that from is already connected to
			@destructor()
		else
			# Connect @from to elem
			@to = elem
			this.redraw()
			@to.addPredecessor(@from, this)
			@from.addSuccessor(@to, this)
			if @from is selected or @to is selected
				@from.highlight()
				@to.highlight()
				@highlight()
			@line.hover(mHoverInConn(this), mHoverOutConn(this))
			@line.mousedown(mMouseDownConn(this))
			@unhighlight() if @from isnt selected and @to isnt selected

	connectTo: (to) ->
		to = getRaphaelParent(to)
		if to?
			# The element will be connected
			@connect(paper.getById(to.raphaelid).data('node'))
		else
			# Connection wasn't dropped over raphael object, delete
			@destructor()

	redraw: (s = 'C', to = @to.getTargetPoint()) ->
		[a, b] = @from.getSourcePoint()
		[c, d] = to
		if s is 'C'
			@line.attr('path', ['M', a, b, s, a + 50, b, c - 50, d, c, d].join(','))
		else
			@line.attr('path', ['M', a, b, s, c, d].join(','))

	highlight: ->
		@line.animate({'stroke-width': 6}, _delay)

	unhighlight: ->
		@line.animate({'stroke-width': 3}, _delay)

	select: ->
		selected.deselect() if selected isnt null
		selected = this
		# Hide all other forms; show respective settings
		$('#preferences > *').addClass('hidden')
		$('#connectionSettings').removeClass('hidden')

		@highlight()
		@from.highlight()
		@to.highlight()

	deselect: ->
		selected = null
		# Hide all other forms; show Output
		$('#preferences > *').addClass('hidden')
		$('#generatedOutput').removeClass('hidden')
		@unhighlight()
		@from.unhighlight()
		@to.unhighlight()
		generateConfig()

class Generator
	@id = null
	@children = null
	@predecessors = null
	@successors = null
	@rect = null
	@circle = null
	@text = null

	constructor: (@config = config: {}, select = true, @x = paperx + 2 * grid * (globalId % 10 + 1) + 2 * grid * (Math.floor(globalId / 10)), @y = papery + 2 * grid * (globalId % 10 + 1), @rectColor = '#00ccff', @rectSelectColor = '#0077ff', @type = 'Generator', @canBeConnected = false, @r = 0) ->
		@children = []
		@predecessors = {}
		@successors = {}

		@id = globalId++

		@circle = paper.circle(@x + 120, @y + 30, 10)
		@circle.attr({'fill': '#ffffff', 'stroke-width': 2, 'cursor': 'crosshair'})

		@rect = paper.rect(@x, @y, 120, 60, @r)
		@rect.attr({'fill': @rectColor, 'cursor': 'move', 'stroke-width': 2})

		@text = paper.text(@x + 60, @y + 30, @type)
		@text.attr({'font-family': 'sans-serif', 'font-size': 14, 'cursor': 'move'})
		
		@rect.drag(mDragBox(this), mDownBox(this))
		@text.drag(mDragBox(this), mDownBox(this))
		@circle.drag(mDragAccess, mDownAccess, mUpAccess)

		@children.push(@circle)
		@children.push(@rect)
		@children.push(@text)

		node.data('node', this) for node in @children

		allNodes[@type.toLowerCase() + 's'].push(this)

		@select() if select is true
		@setLabel()
		this

	destructor: ->
		this.deselect() if this is selected
		child.remove() for child in @children
		pred.line.destructor() for id, pred of @predecessors
		succ.line.destructor() for id, succ of @successors
		allNodes[@type.toLowerCase() + 's'].splice(allNodes[@type.toLowerCase() + 's'].indexOf(this), 1)
		generateConfig()

	highlight: ->
		@rect.animate({'stroke-width': 4}, _delay)

	unhighlight: ->
		@rect.animate({'stroke-width': 2}, _delay)

	setLabel: ->
		text = ''
		if @config.name? and @config.name isnt ''
			text += @config.name.replace(/^.*\./, '').replace(@type, '').trim() + '\n' + @type
		else
			text += @type
		if @config.id? and @config.id isnt ''
			text += '\n' + @config.id
		@text.attr('text', text)

	# Get config and populate inputs recursively
	getConfig: (config, elem, depth, array = false) ->
		switch elem.dataset.type
			when 'string', 'int', 'long', 'float', 'double'
				if array and config?
					$(elem).val(config)
				else if config[elem.dataset.key]?
					$(elem).val(config[elem.dataset.key])
				else
					$(elem).val(elem.dataset.default)
			when 'JsObject', 'any'
				try
					if array and config isnt undefined
						$(elem).val(JSON.stringify(config, null, '  '))
					else if config[elem.dataset.key] isnt undefined
						$(elem).val(JSON.stringify(config[elem.dataset.key], null, '  '))
					else
						$(elem).val(JSON.stringify(JSON.parse(elem.dataset.default), null, '  '))
					if ($(elem).val().startsWith("\"$JSON.parse{") or $(elem).val().startsWith("\"#JSON.parse{")) and $(elem).val().endsWith("}\"")
						$(elem).val($(elem).val().substring(1, $(elem).val().length - 1))
				catch
					$(elem).val('')
			when 'boolean'
				if array and config?
					$(elem).prop('checked', config)
				if config[elem.dataset.key]?
					$(elem).prop('checked', config[elem.dataset.key])
				else
					$(elem).prop('checked', elem.dataset.default is 'true')
			when 'object'
				if array and config?
					for child in $(elem).find('*[data-depth="' + (depth + 1) + '"]')
						@getConfig(config, child, depth + 1)
				else if config[elem.dataset.key]?
					for child in $(elem).find('*[data-depth="' + (depth + 1) + '"]')
						@getConfig(config[elem.dataset.key], child, depth + 1)
				else
					for child in $(elem).find('*[data-depth="' + (depth + 1) + '"]')
						@getConfig({}, child, depth + 1)
			when 'array'
				# Remove old values
				$(elem).find('> div[data-arraytype="value"]').remove()
				# Add new elems
				newElems = if array then config else if config[elem.dataset.key]? then config[elem.dataset.key] else []
				for data in newElems
					type = if _.isArray(data) then 'array' else typeof(data)
					prototype = $(elem).find('> div[data-arraytype="prototype"] *[data-depth="' + (depth + 1) + '"][data-type="' + type + '"]').first()
					# Try to find best match where applicable
					if prototype.length is 0 and type is 'object'
						prototype = $(elem).find('> div[data-arraytype="prototype"] *[data-depth="' + (depth + 1) + '"][data-type="JsObject"]').first()
					if prototype.length is 0
						prototype = $(elem).find('> div[data-arraytype="prototype"] *[data-depth="' + (depth + 1) + '"][data-type="any"]').first()
					newElem = prototype.closest('div[data-arraytype="prototype"]').clone(true).removeClass('hidden').attr('data-arraytype', 'value').appendTo($(elem))
					newElem.find('*[data-depth="' + (depth + 1) + '"]').each((i, el) => @getConfig(data, el, depth + 1, true))
					newElem.find('*[data-toggle="tooltip"]').each( (i, el) ->
						$(el).tooltip() if $(el).closest('div[data-arraytype="prototype"]').length is 0
					)
		return

	# Set config recursively
	setConfig: (config, elem, depth, array = false) ->
		if array
			nextElements = $(elem).find('div[data-arraytype="value"] *[data-depth="' + depth + '"]')
		else
			nextElements = $(elem).find('*[data-depth="' + depth + '"]')
		for data in nextElements
			do (data) =>
				myDefault = undefined
				try
					myDefault = JSON.parse(data.dataset.default)
				myValue = undefined
				myValue = switch data.dataset.type
					when 'string'
						$(data).val() if $(data).prop('required') is true or ($(data).val() isnt '' and $(data).val() isnt data.dataset.default)

					when 'JsObject'
						if ($(data).val().startsWith("$JSON.parse{") or $(data).val().startsWith("#JSON.parse{")) and $(data).val().endsWith("}")
							$(data).val()
						else
							try
								JSON.parse($(data).val()) if _.isObject(JSON.parse($(data).val())) and not _.isArray(JSON.parse($(data).val())) and ($(data).prop('required') is true or not _.isEqual(JSON.parse($(data).val()), myDefault))

					when 'any'
						if ($(data).val().startsWith("$JSON.parse{") or $(data).val().startsWith("#JSON.parse{")) and $(data).val().endsWith("}")
							$(data).val()
						else
							try
								JSON.parse($(data).val()) if $(data).prop('required') is true or ($(data).val() isnt '' and not _.isEqual(JSON.parse($(data).val()), myDefault))

					when 'int', 'long'
						if ($(data).val().startsWith("$JSON.parse{") or $(data).val().startsWith("#JSON.parse{")) and $(data).val().endsWith("}")
							$(data).val()
						else
							$(intInputTester).prop('required', $(data).prop('required'))
							$(intInputTester).val($(data).val())
							if not intInputTester.validity.valid or $(intInputTester).val() is ''
								undefined
							else
								parseFloat($(intInputTester).val())

					when 'float', 'double'
						if ($(data).val().startsWith("$JSON.parse{") or $(data).val().startsWith("#JSON.parse{")) and $(data).val().endsWith("}")
							$(data).val()
						else
							$(floatInputTester).prop('required', $(data).prop('required'))
							$(floatInputTester).val($(data).val())
							if not floatInputTester.validity.valid or $(floatInputTester).val() is ''
								undefined
							else
								parseFloat($(floatInputTester).val())

					when 'boolean'
						$(data).prop('checked') if $(data).prop('required') is true or $(data).prop('checked').toString() isnt data.dataset.default

					when 'object'
						newObject = {}
						@setConfig(newObject, data, depth + 1)
						if _.isEmpty(newObject) and data.dataset.required is 'false' then undefined else newObject

					when 'array'
						newArray = []
						@setConfig(newArray, data, depth + 1, true)
						if _.isEmpty(newArray) and data.dataset.required is 'false' then undefined else newArray

					else undefined

				if array
					config.push(myValue) if myValue isnt undefined
				else
					config[data.dataset.key] = myValue if myValue isnt undefined
		return

	activateForm: ->
		# Hide all forms, show corresponding form
		$('#preferences > *').addClass('hidden')
		$('#' + @type.toLowerCase() + 'Settings').removeClass('hidden')
		$('#' + @type.toLowerCase() + 'Name').val(@config.name)

		# Hide all shown class sub-forms
		$('#preferences > * > div[data-class]').addClass('hidden')
		dataClass = $('#preferences > * > div[data-class="' + @config.name + '"]')
		dataClass.removeClass('hidden')

		# Populate inputs
		dataClass.find('*[data-depth="0"]').each((i, data) => @getConfig(@config, data, 0))

		# Update validation classes
		dataClass.find('input[type="text"],input[type="number"],textarea').each((i, e) -> checkValidity(e))

	deactivateForm: ->
		# Hide all forms, show Output
		$('#preferences > *').addClass('hidden')
		$('#generatedOutput').removeClass('hidden')

		data = $('#preferences div[data-class="' + @config.name + '"]')
		if data.length > 0
			name = @config.name
			@config = {}
			@setConfig(@config, data[0], 0)
			@config.name = name

	select: ->
		selected.deselect() if selected isnt null
		selected = this
		for node in @children
			node.toFront()
		@activateForm()
		for id, succ of @successors
			succ.node.highlight()
			succ.line.highlight()
		for id, pred of @predecessors
			pred.node.highlight()
			pred.line.highlight()
		@highlight()
		@rect.animate({'fill': @rectSelectColor}, _delay)

	deselect: ->
		@deactivateForm()
		@setLabel()
		selected = null

		# Unhighlight neighbors and their connections
		for id, succ of @successors
			succ.node.unhighlight()
			succ.line.unhighlight()
		for id, pred of @predecessors
			pred.node.unhighlight()
			pred.line.unhighlight()
		@unhighlight()
		@rect.animate({'fill': @rectColor}, _delay)
		generateConfig()

	getSourcePoint: ->
		[@circle.attr('cx'), @circle.attr('cy')]

	addPredecessor: (neighbor, line) ->
		@predecessors[neighbor.id] =
			node: neighbor
			line: line

	removePredecessor: (neighbor) ->
		delete @predecessors[neighbor.id]

	addSuccessor: (neighbor, line) ->
		@successors[neighbor.id] =
			node: neighbor
			line: line

	removeSuccessor: (neighbor) ->
		delete @successors[neighbor.id]

	redrawConnections: ->
		pred.line.redraw() for id, pred of @predecessors
		succ.line.redraw() for id, succ of @successors


class Processor extends Generator
	@targetInner = null
	@targetOuter = null

	constructor: (config = config: {}, select = true, @x = paperx + 20 * grid + 2 * grid * (globalId % 10 + 1) + 2 * grid * (Math.floor(globalId / 10)), @y = papery + 2 * grid * (globalId % 10 + 1)) ->
		super(config, select, @x, @y, '#00ff66', '#00bb00', 'Processor', true, 10)

		@targetOuter = paper.circle(@x, @y + 30, 10)
		@targetOuter.attr({'fill': '#ffffff', 'stroke-width': 2, 'cursor': 'crosshair'})
		@targetInner = paper.circle(@x, @y + 30, 8)
		@targetInner.attr({'fill': '#000000', 'stroke': '#ffffff', 'stroke-width': 2, 'cursor': 'crosshair'})

		@targetOuter.insertBefore(@rect)
		@targetInner.insertBefore(@rect)

		@children = [@targetOuter, @targetInner].concat(@children)

		node.data('node', this) for node in @children
		this

	getTargetPoint: ->
		[@targetInner.attr('cx'), @targetInner.attr('cy')]

getConfig = ->
	# Cycle through all nodes and add the array of successors to its config
	gen = for g in allNodes.generators
		conf = g.config
		conf.modeller = { 'x': Number(g.rect.attr('x')), 'y': Number(g.rect.attr('y')) }
		conf.next = []
		for id, succ of g.successors
			conf.next.push(succ.node.config.id)
		conf
	pro = for p in allNodes.processors
		conf = p.config
		conf.modeller = { 'x': Number(p.rect.attr('x')), 'y': Number(p.rect.attr('y')) }
		conf.next = []
		for id, succ of p.successors
			conf.next.push(succ.node.config.id)
		conf
	# Build config
	json =
		generators:  gen
		processors:  pro

generateConfig = (e) ->
	e.preventDefault() if e?
	selected.deselect() if selected?
	# Get actual config
	json = getConfig()

	$('#outputTextarea').val(JSON.stringify(json, null, '    ')).focus()
	$('#preferences > *').addClass('hidden')
	$('#generatedOutput').removeClass('hidden')

importConfig = (object) ->
	try
		for el in allNodes.generators.concat(allNodes.processors)
			el.destructor()
		globalId = 0

		nodesPerRow = Math.floor(document.getElementById('flowchart').clientWidth / 200)

		processorIds = {}
		for processor in object.processors
			processorIds[processor.id] = processor
		ids = {}
		pros = 0
		newConnections = []

		importProcessor = (id) ->
			if not _.has(processorIds, id)
				console.log("Processor with id #{id} was not found.")
				return
			if not _.has(ids, id)
				ids[id] = new Processor(
					processorIds[id],
					false,
					if processorIds[id].modeller? and processorIds[id].modeller.x? then processorIds[id].modeller.x else paperx + grid + (pros % (nodesPerRow - 1) + 1) * grid * Math.ceil(200 / grid),
					if processorIds[id].modeller? and processorIds[id].modeller.y? then processorIds[id].modeller.y else papery + grid + Math.floor(pros / (nodesPerRow - 1)) * 100)
				pros += 1
				for succ in processorIds[id].next
					importProcessor(succ)
					newConnections.push([ids[id], succ])

		# Start from all generators and build their descendants recursively, depth first
		for generator, i in object.generators
			gen = new Generator(
				generator,
				false,
				if generator.modeller? and generator.modeller.x? then generator.modeller.x else paperx + grid,
				if generator.modeller? and generator.modeller.y? then generator.modeller.y else papery + grid + i * grid * Math.ceil(100 / grid))
			for succ in generator.next
				importProcessor(succ)
				newConnections.push([gen, succ])

		# Import processors that don't have a generator ancestor
		for processor in object.processors
			importProcessor(processor.id)

		for pair in newConnections
			if not _.has(ids, pair[1])
				console.log("Processor with id #{id} was not found.")
			else
				new Connection(pair[0], ids[pair[1]])

# Load config
importConfig(loadedConfig)

# Saved config, keep track to remind user if he really wants to leave
savedConfig = loadedConfig

# Bind click events
$('a[href="#GenerateConfig"]').on('click', generateConfig)

$('a[href="#SaveConfig"]').on('click', (e) ->
	e.preventDefault()
	config = getConfig()
	jsRoutes.controllers.modeller.Application.saveConfig(file).ajax(
		contentType: "application/json; charset=utf-8",
		data: JSON.stringify(config),
		success: ->
			resultSymbol = document.createElement('span')
			resultSymbol.className = "glyphicon glyphicon-ok-circle"
			e.target.parentNode.insertBefore(resultSymbol, e.target.nextSibling)
			$(resultSymbol).fadeOut(5000, -> e.target.parentNode.removeChild(resultSymbol))
			savedConfig = config
		error: ->
			resultSymbol = document.createElement('span')
			resultSymbol.className = "glyphicon glyphicon-remove-circle"
			e.target.parentNode.insertBefore(resultSymbol, e.target.nextSibling)
			$(resultSymbol).fadeOut(5000, -> e.target.parentNode.removeChild(resultSymbol))
	)
)

$('a[href="#AddGenerator"]').on('click', (e) ->
	e.preventDefault()
	new Generator()
	document.activeElement.blur()
)
$('a[href="#AddProcessor"]').on('click', (e) ->
	e.preventDefault()
	new Processor()
	document.activeElement.blur()
)
$('#preferences button[name="deleteSelected"]').on('click', (e) ->
	e.preventDefault()
	selected.destructor()
)
$('#preferences button[name="copySelected"]').on('click', (e) ->
	e.preventDefault()
	if selected.type is 'Generator'
		new Generator(selected.config)
	else if selected.type is 'Processor'
		new Processor(selected.config)
)
$('#preferences button[name="AddArrayElement"]').on('click', (e) ->
	thatArray = $(this).closest('*[data-type="array"]')
	$(this).closest('.form-group').nextAll('div[data-arraytype="prototype"]').first().clone(true).removeClass('hidden').attr('data-arraytype', 'value').appendTo(thatArray).find('*[data-toggle="tooltip"]').each( (i, el) ->
		$(el).tooltip() if $(el).closest('div[data-arraytype="prototype"]').length is 0
	)
)
$('#preferences button[name="DeleteArrayElement"]').on('click', ->
	$(this).closest('div[data-arraytype="value"]').remove()
)
# Highlight which element(s) will be deleted
$('#preferences button[name="DeleteArrayElement"]').hover(
	-> $(this).closest('div[data-arraytype="value"]').css('background-color', '#f2dede').find('div[data-depth]').css('background-color', '#f2dede'),
	-> $(this).closest('div[data-arraytype="value"]').css('background-color', '').find('div[data-depth]').css('background-color', '')
)

# Takes a DOM element and handles validity of its input using has-error and has-warning css classes
checkValidity = (elem) ->
	switch elem.dataset.type

		when 'string'
			if elem.validity.valid is false
				$(elem).closest('.form-group').addClass('has-warning')
			else
				$(elem).closest('.form-group').removeClass('has-warning')

		when 'int', 'long'
			if ($(elem).val().startsWith("$JSON.parse{") or $(elem).val().startsWith("#JSON.parse{")) and $(elem).val().endsWith("}")
				$(elem).closest('.form-group').removeClass('has-error')
			else
				$(intInputTester).prop('required', $(elem).prop('required'))
				$(intInputTester).val($(elem).val())
				if not intInputTester.validity.valid or ($(intInputTester).val() is '' and ($(elem).val() isnt '' or $(elem).prop('required')))
					$(elem).closest('.form-group').addClass('has-error')
				else
					$(elem).closest('.form-group').removeClass('has-error')

		when 'float', 'double'
			if ($(elem).val().startsWith("$JSON.parse{") or $(elem).val().startsWith("#JSON.parse{")) and $(elem).val().endsWith("}")
				$(elem).closest('.form-group').removeClass('has-error')
			else
				$(floatInputTester).prop('required', $(elem).prop('required') is true)
				$(floatInputTester).val($(elem).val())
				if not floatInputTester.validity.valid or ($(floatInputTester).val() is '' and ($(elem).val() isnt '' or $(elem).prop('required') is true))
					$(elem).closest('.form-group').addClass('has-error')
				else
					$(elem).closest('.form-group').removeClass('has-error')

		when 'JsObject'
			if ($(elem).val().startsWith("$JSON.parse{") or $(elem).val().startsWith("#JSON.parse{")) and $(elem).val().endsWith("}")
				$(elem).closest('.form-group').removeClass('has-error')
			else if $(elem).prop('required') is false and $(elem).val() is ''
				$(elem).closest('.form-group').removeClass('has-error')
			else
				try
					if _.isObject(JSON.parse($(elem).val())) and not _.isArray(JSON.parse($(elem).val()))
						$(elem).closest('.form-group').removeClass('has-error')
					else
						$(elem).closest('.form-group').addClass('has-error')
				catch
					$(elem).closest('.form-group').addClass('has-error')

		when 'any'
			if ($(elem).val().startsWith("$JSON.parse{") or $(elem).val().startsWith("#JSON.parse{")) and $(elem).val().endsWith("}")
				$(elem).closest('.form-group').removeClass('has-error')
			else if $(elem).prop('required') is false and $(elem).val() is ''
				$(elem).closest('.form-group').removeClass('has-error')
			else
				try
					JSON.parse($(elem).val())
					$(elem).closest('.form-group').removeClass('has-error')
				catch
					$(elem).closest('.form-group').addClass('has-error')
	return

# Bind respective input types to check validity
$('#preferences').find('
	input[data-type="string"],
	input[data-type="int"],
	input[data-type="long"],
	input[data-type="float"],
	input[data-type="double"],
	textarea[data-type="JsObject"],
	textarea[data-type="any"]
').on('input', -> checkValidity(this))

$('#generatorName,#processorName').on('change', ->
	selected.deactivateForm()
	selected.config.name = $(this).val()
	selected.setLabel()
	delete selected.config.config
	selected.activateForm()
)

$('*[data-toggle="tooltip"]').each( (i, el) ->
	$(el).tooltip() if $(el).closest('div[data-arraytype="prototype"]').length is 0
)

$('div[data-type="array"]').sortable({
	items: '> div[data-arraytype="value"]'
})

# Bind delete key to node deletion if active element is window
document.addEventListener('keydown', (e) ->
	if e.keyCode is 46 and document.activeElement.nodeName is 'BODY'
		selected.destructor()
)

# Ask user if he really wants to leave
window.onbeforeunload = (e) ->
	if not _.isEqual(savedConfig, getConfig())
		'It was detected that you may have unsaved changes. Please confirm that you want to leave - data you have entered may not be saved.'

# Activate processor and generator filters
$('#filterGenerators,#filterProcessors').each( (i, el) ->
	$(el).on('change', (e) ->
		$(el).closest('form').find('select').first().find('> optgroup').each( (i, optgroup) ->
			toBeHidden_group = true
			$(optgroup).find('> option').each( (j, option) ->
				value = $(option).val()
				title = $(option).text()
				toBeHidden_value = false
				toBeHidden_title = false
				keywords = $(el).val().split(' ')
				for str in keywords
					if str.trim() isnt ''
						re = new RegExp(str.trim().replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, '\\$&'), 'i')
						if not re.test(value)
							toBeHidden_value = true
						if not re.test(title)
							toBeHidden_title = true
					if toBeHidden_value and toBeHidden_title
						break
				if toBeHidden_value and toBeHidden_title
					$(option).addClass('hidden')
				else
					$(option).removeClass('hidden')
					toBeHidden_group = false
				return
			)
			if toBeHidden_group is true
				$(optgroup).addClass('hidden')
			else
				$(optgroup).removeClass('hidden')
			return
		)
		return
	)
	return
)

# Web Socket
$('a[href="#RunConfig"]').on('click', (e) ->
	e.preventDefault()

	# Open WebSocket
	ws = new WebSocket(webSocketUrl)
	ws.onopen = ->
		# Send config to run
		ws.send(JSON.stringify(getConfig()))
	ws.onmessage = (msg) ->
		data = JSON.parse(msg.data)
		if data is true
			resultSymbol = document.createElement('span')
			resultSymbol.className = "glyphicon glyphicon-ok-circle"
			e.target.parentNode.insertBefore(resultSymbol, e.target.nextSibling)
			$(resultSymbol).fadeOut(5000, -> e.target.parentNode.removeChild(resultSymbol))
		else if data is false
			resultSymbol = document.createElement('span')
			resultSymbol.className = "glyphicon glyphicon-remove-circle"
			e.target.parentNode.insertBefore(resultSymbol, e.target.nextSibling)
			$(resultSymbol).fadeOut(5000, -> e.target.parentNode.removeChild(resultSymbol))
		else
			processor = _.find(allNodes.processors, (pro) -> pro.config.id is data.processor_id)
			if processor isnt undefined
				if data.type is 'EndType'
					$(processor.circle.node)
						.attr('title', JSON.stringify(data.data, null, '    '))
						.tooltip({
							container: 'body'
							placement: 'bottom'
						})
						.tooltip('fixTitle')
				else if data.type is 'BeginType'
					$([processor.targetInner.node, processor.targetOuter.node])
						.attr('title', JSON.stringify(data.data, null, '    '))
						.tooltip({
							container: 'body'
							placement: 'bottom'
						})
						.tooltip('fixTitle')
)