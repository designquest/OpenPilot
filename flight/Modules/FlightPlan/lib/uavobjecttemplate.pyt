##
##############################################################################
#
# @file       $(NAMELC).py
# @author     The OpenPilot Team, http://www.openpilot.org Copyright (C) 2010.
# @brief      Implementation of the $(NAME) object. This file has been
#             automatically generated by the UAVObjectGenerator. For use with
#             the PyMite VM of the FlightPlan module.
#
# @note       Object definition file: $(XMLFILE).
#             This is an automatically generated file.
#             DO NOT modify manually.
#
# @see        The GNU Public License (GPL) Version 3
#
#############################################################################/
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
# or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
# for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
#


from uavobject import *

$(DATAFIELDS)

# Object $(NAME) definition
class $(NAME)(UAVObject):
	# Object constants
	OBJID        = $(UOBJID)
        name         = '$(NAME)'
        description  = '$(DESCRIPTION)'
        isSingleInst = $(ISSINGLEINST)

	# Constructor
	def __init__(self):
		UAVObject.__init__(self, $(NAME).OBJID)

		# Create object fields
$(DATAFIELDINIT)
		# Read field data
		self.read()
		self.metadata.read()









