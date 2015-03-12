paperx = 0
papery = 0
paperdx = 0
paperdy = 0
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
	this.data('line').connect(event.target)

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
	conn.select()

class Connection
	@to = null
	@line = null

	constructor: (@from) ->
		@line = paper.path('M0,0l0,0')
		@line.attr('stroke-width': 3)
		@line.insertAfter(background)
		@highlight()

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

	connect: (to) ->
		to = getRaphaelParent(to)
		if to isnt null
			# The element will be connected
			elem = paper.getById(to.raphaelid).data('node')
			if elem is undefined or elem.canBeConnected isnt true or elem is @from or elem.id of @from.successors
				# Connection was not dropped over a node that can be connected;
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
		$('#preferences > *').each( -> $(this).addClass('hidden') )
		$('#connectionSettings').each( -> $(this).removeClass('hidden') )

		@highlight()
		@from.highlight()
		@to.highlight()

	deselect: ->
		selected = null
		# Hide all other forms; show Output
		$('#preferences > *').each( -> $(this).addClass('hidden') )
		$('#generatedOutput').each( -> $(this).removeClass('hidden') )
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

	constructor: (@rectColor = '#00ccff', @rectSelectColor = '#0077ff', @type = 'Generator', @canBeConnected = false, @r = 0) ->
		@children = []
		@predecessors = {}
		@successors = {}

		@config =
			config: {}

		@id = globalId++
		@x = paperx + grid * (@id % 10 + 1) + grid * (Math.floor(@id / 10))
		@y = papery + grid * (@id % 10 + 1)

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

		@select()
		return

	destructor: ->
		this.deselect() if this is selected
		child.remove() for child in @children
		pred.line.destructor() for id, pred of @predecessors
		succ.line.destructor() for id, succ of @successors
		allNodes[@type.toLowerCase() + 's'].splice(allNodes[@type.toLowerCase() + 's'].indexOf(this))
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

	# Get config for a specific DOM element
	getConfig: (elem) ->
		configs = []
		parent = elem.parentNode
		while (parent?)
			if (parent? and parent.dataset? and parent.dataset.config?)
				configs.push(parent.dataset.config)
			parent = parent.parentNode
		config = @config
		for conf in configs
			config = config[conf]
		config[elem.name]

	# Set config for a specific DOM element
	setConfig: (elem, newValue = $(elem).prop('value')) ->
		name = elem.name
		configs = []
		while (elem?)
			elem = elem.parentNode
			if (elem? and elem.dataset? and elem.dataset.config?)
				configs.push(elem.dataset.config)
		config = @config
		for conf in configs
			config = config[conf]
		config[name] = newValue

	activateForm: ->
		# Hide all forms, show corresponding form
		$('#preferences > *').each( -> $(this).addClass('hidden') )
		$('#' + @type.toLowerCase() + 'Settings').each( -> $(this).removeClass('hidden') )
		$('#' + @type.toLowerCase() + 'Name').val(@config.name)
		# Hide all shown class sub-forms
		$('#preferences > * > *[data-class]').each( -> $(this).addClass('hidden') )
		$('#preferences div[data-class="' + @config.name + '"]').each( -> $(this).removeClass('hidden'))

		# Populate inputs
		$('#preferences div[data-class="' + @config.name + '"] input[type="text"],#preferences div[data-class="' + @config.name + '"] input[type="number"]').each( ->
			val = selected.getConfig(this)
			if val?
				$(this).prop('value', val)
			else
				$(this).prop('value', '')
		)
		$('#preferences div[data-class="' + @config.name + '"] input[type="checkbox"]').each( ->
			val = selected.getConfig(this)
			if val?
				$(this).prop('checked', val)
			else
				$(this).prop('checked', false)
		)
		$('#preferences div[data-class="' + @config.name + '"] select').each( ->
			val = selected.config[@parentNode.name]
			if val?
				$(this).val(val)
			else
				$(this).val('')
		)

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
		selected = null
		# Hide all forms, show Output
		$('#preferences > *').each( -> $(this).addClass('hidden') )
		$('#generatedOutput').each( -> $(this).removeClass('hidden') )

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

	constructor: ->
		super('#00ff66', '#00bb00', 'Processor', true, 10)

		@targetOuter = paper.circle(@x, @y + 30, 10)
		@targetOuter.attr({'fill': '#ffffff', 'stroke-width': 2, 'cursor': 'crosshair'})
		@targetInner = paper.circle(@x, @y + 30, 8)
		@targetInner.attr({'fill': '#000000', 'stroke': '#ffffff', 'stroke-width': 2, 'cursor': 'crosshair'})

		@targetOuter.insertBefore(@rect)
		@targetInner.insertBefore(@rect)

		@children = [@targetOuter, @targetInner].concat(@children)

		node.data('node', this) for node in @children
		return

	getTargetPoint: ->
		[@targetInner.attr('cx'), @targetInner.attr('cy')]

generateConfig = (e) ->
	e.preventDefault() if e?
	gen = for g in allNodes.generators
		conf = g.config
		conf.next = []
		for id, succ of g.successors
			conf.next.push(succ.node.config.id)
		conf
	pro = for p in allNodes.processors
		conf = p.config
		conf.next = []
		for id, succ of p.successors
			conf.next.push(succ.node.config.id)
		conf
	json =
		generators:  gen
		processors:  pro
	document.getElementById('outputTextarea').value = JSON.stringify(json, null, '    ')
	selected.deselect() if selected?

# Bind AddGenerator, AddProcessor and deleteSelected respective click events
$('a[href="#AddGenerator"]').on('click', (e) ->
	e.preventDefault()
	new Generator()
)
$('a[href="#AddProcessor"]').on('click', (e) ->
	e.preventDefault()
	new Processor()
)
$('#preferences button[name="deleteSelected"]').on('click', (e) ->
	e.preventDefault()
	selected.destructor()
)

$('a[href="#GenerateConfig"]').on('click', generateConfig)

# Bind respective input types to change selected.config
$('#preferences input[type="text"]').on('input', ->
	selected.setConfig(this)
)
$('#preferences input[type="number"][step="1"]').on('input', ->
	newValue = parseInt($(this).prop('value'))
	selected.setConfig(this, newValue) if not isNaN(newValue)
)
$('#preferences input[type="checkbox"]').on('change', ->
	selected.setConfig(this, $(this).prop('checked'))
)
$('#preferences select').on('change', ->
	selected.config[@name] = $(this).prop('value')
	selected.config.config = {}
	selected.setLabel()
	selected.activateForm()
)
