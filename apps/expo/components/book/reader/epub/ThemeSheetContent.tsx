import { useRef } from 'react'
import { View } from 'react-native'
import PagerView from 'react-native-pager-view'

import { Brightness, FontConfig, ThemeSelect } from './controls'
import CustomizeTheme from './controls/CustomizeTheme'

export default function ThemeSheetContent() {
	const pagerRef = useRef<PagerView>(null)

	return (
		<PagerView
			ref={pagerRef}
			initialPage={0}
			style={{ flex: 1 }}
			scrollEnabled={false}
			// onPageSelected={(e) => setActivePage(e.nativeEvent.position)}
		>
			<View className="flex-1 gap-8 bg-background pt-4" key="0">
				<Brightness />

				<ThemeSelect onCustomizePress={() => pagerRef.current?.setPage(1)} />

				<FontConfig />
			</View>

			<View className="flex-1 gap-8" key="1">
				<CustomizeTheme onCancel={() => pagerRef.current?.setPage(0)} />
			</View>
		</PagerView>
	)
}
