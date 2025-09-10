import { ALargeSmall, ChevronRight } from 'lucide-react-native'
import { Platform, View } from 'react-native'

import { Text } from '~/components/ui'
import { Icon } from '~/components/ui/icon'
import { cn } from '~/lib/utils'

import FontSizeSlider from './FontSizeSlider'

export default function FontConfig() {
	return (
		<View className="">
			<View className="flex-row px-4 py-2">
				<FontSizeSlider />
			</View>

			<View className="flex-row justify-between px-4 py-2">
				<View className="flex-row items-center gap-4">
					<Icon as={ALargeSmall} className="h-6 w-6 text-foreground-muted" />
					<Text className="text-lg text-foreground">Font</Text>
				</View>

				<View className="flex-row items-center gap-4">
					<Text className="text-lg text-foreground">Name</Text>
					<Icon as={ChevronRight} className="h-6 w-6 text-foreground-muted" />
				</View>
			</View>

			<Divider />
		</View>
	)
}

const Divider = () => (
	<View
		className={cn('h-px w-full bg-edge')}
		style={{
			// 16 + 24 + 8 = 48
			marginLeft: Platform.OS === 'android' ? 0 : 48,
		}}
	/>
)
